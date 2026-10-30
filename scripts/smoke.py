"""Container-friendly HTTP acceptance checks; Python standard library only.

Run after `docker compose up --build --wait` via the compose.test.yml smoke profile,
or use BASE_URL=http://localhost:3000 python scripts/smoke.py. Uses fresh identities
and creates demonstration data; never deletes existing users, tasks or files.
"""

import base64
import json
import os
import time
import uuid
from datetime import datetime
from urllib.error import HTTPError
from urllib.parse import quote, urljoin
from urllib.request import Request, urlopen


BASE_URL = os.environ.get("BASE_URL", "http://localhost:3000").rstrip("/")
STAGES = ["CHECKING", "GENERATING", "FINALIZING"]
TERMINAL = {"SUCCEEDED", "FAILED"}
# A complete, valid 1 x 1 RGB PNG, with actual image data and valid chunk CRCs.
PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGMImrYUAALLAY57tdOfAAAAAElFTkSuQmCC"
)


def expect(condition, message):
    if not condition:
        raise AssertionError(message)


def request(method, path, *, token=None, payload=None, body=None, content_type=None,
            expected=(200,), binary=False):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if payload is not None:
        body = json.dumps(payload).encode()
        content_type = "application/json"
    if content_type:
        headers["Content-Type"] = content_type
    req = Request(urljoin(BASE_URL + "/", path), data=body, headers=headers, method=method)
    try:
        response = urlopen(req, timeout=15)
    except HTTPError as error:
        response = error
    with response:
        status = response.status
        data = response.read()
        response_type = response.headers.get("Content-Type", "")
    expect(status in expected,
           f"{method} {path}: expected {expected}, received {status}: {data[:400]!r}")
    if binary:
        return data, response_type
    try:
        result = json.loads(data)
    except (ValueError, UnicodeDecodeError) as error:
        raise AssertionError(f"{method} {path}: invalid JSON: {data[:200]!r}") from error
    if status >= 400:
        expect(isinstance(result, dict) and bool(result.get("message")),
               f"{method} {path}: error must contain message")
    return result


def identity(label):
    result = request("POST", "/api/identities",
                     payload={"displayName": f"smoke-{label}-{uuid.uuid4().hex[:8]}"},
                     expected=(200, 201))
    expect(all(result.get(key) for key in ("id", "displayName", "token")),
           "Identity must include id, displayName and token")
    me = request("GET", "/api/me", token=result["token"])
    expect(me["id"] == result["id"], "GET /api/me returned a different identity")
    return result


def upload(token, *, data=PNG, filename="smoke.png", mime_type="image/png",
           expected=(200, 201)):
    boundary = f"smoke-{uuid.uuid4().hex}"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: {mime_type}\r\n\r\n"
    ).encode() + data + f"\r\n--{boundary}--\r\n".encode()
    return request("POST", "/api/assets", token=token, body=body,
                   content_type=f"multipart/form-data; boundary={boundary}", expected=expected)


def submit(token, template, assets, *, delay=500, fail=None, expected=(202,)):
    simulation = {"delayMs": delay}
    if fail:
        simulation["failStage"] = fail
    return request("POST", "/api/tasks", token=token, expected=expected, payload={
        "templateId": template["id"], "assetIds": [asset["id"] for asset in assets],
        "simulation": simulation,
    })


def timestamp(value):
    expect(isinstance(value, str) and bool(value), "Missing persisted timestamp")
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def verify_terminal(task, failed_stage=None, minimum_step_seconds=0):
    steps = task["steps"]
    expect([step["stage"] for step in steps] == STAGES, "Task must have three ordered phases")
    expect(timestamp(task["createdAt"]) <= timestamp(task["updatedAt"]),
           "Task timestamps are reversed")
    previous_end = None
    for index, step in enumerate(steps):
        failure_index = STAGES.index(failed_stage) if failed_stage else len(STAGES)
        expected_status = "SUCCEEDED" if index < failure_index else (
            "FAILED" if index == failure_index else "PENDING"
        )
        expect(step["status"] == expected_status,
               f"{task['id']} {step['stage']}: expected {expected_status}, got {step['status']}")
        if expected_status == "PENDING":
            expect(not step.get("startedAt") and not step.get("finishedAt"),
                   "Phases after failure must never start")
        else:
            started, finished = timestamp(step["startedAt"]), timestamp(step["finishedAt"])
            expect(started <= finished, "Phase ended before it started")
            expect((finished - started).total_seconds() >= minimum_step_seconds,
                   "Persisted phase duration ignored simulation.delayMs")
            if previous_end:
                expect(previous_end <= started, "Phases must execute sequentially")
            previous_end = finished
        if expected_status == "FAILED":
            expect(bool(step.get("errorMessage")), "Failed phase needs a persisted reason")
    if failed_stage:
        expect(task["status"] == "FAILED" and task["stage"] == failed_stage,
               "Task must remain at the failed phase")
        expect(bool(task.get("errorMessage")) and not task.get("resultUrl"),
               "Failed task needs a reason and must not expose a successful result")
    else:
        expect(task["status"] == "SUCCEEDED" and task["stage"] == "COMPLETED",
               "Successful task must reach COMPLETED")
        expect(bool(task.get("resultUrl")), "Successful task needs a result URL")


def poll_all(token, task_ids):
    remaining = set(task_ids)
    results = {}
    deadline = time.monotonic() + 75
    while remaining and time.monotonic() < deadline:
        for task_id in tuple(remaining):
            task = request("GET", f"/api/tasks/{task_id}", token=token)
            expect(task["id"] == task_id, "Polling mixed up task IDs")
            expect(task["status"] in TERMINAL | {"QUEUED", "RUNNING"}, "Invalid task status")
            expect([step["stage"] for step in task["steps"]] == STAGES, "Missing phase snapshot")
            if task["status"] in TERMINAL:
                results[task_id] = task
                remaining.remove(task_id)
        if remaining:
            time.sleep(0.2)
    expect(not remaining, f"Tasks did not finish before timeout: {remaining}")
    return results


def main():
    page, _ = request("GET", "/", binary=True)
    expect(b"<html" in page.lower(), "Frontend root must serve HTML")
    request("GET", "/api/tasks", expected=(401,))
    request("GET", "/api/me", token="invalid-smoke-token", expected=(401,))
    alice, bob = identity("alice"), identity("bob")
    expect(alice["id"] != bob["id"] and alice["token"] != bob["token"],
           "Identities must be distinct")
    token = alice["token"]

    templates = request("GET", "/api/templates", token=token)
    expect(isinstance(templates, list) and len(templates) >= 3, "Expected multiple persisted templates")
    categories = {template["category"] for template in templates}
    expect(len(categories) >= 2, "Expected multiple template categories")
    template = min(templates, key=lambda item: item["requiredImageCount"])
    detail = request("GET", f"/api/templates/{template['id']}", token=token)
    for field in ("id", "title", "category", "coverUrl", "description", "requiredImageCount",
                  "acceptedMimeTypes", "maxFileSizeBytes"):
        expect(detail.get(field), f"Template detail missing {field}")
    filtered = request("GET", "/api/templates?category=" + quote(template["category"]), token=token)
    expect(bool(filtered) and all(t["category"] == template["category"] for t in filtered),
           "Category filtering must occur in the API")
    cover, cover_type = request("GET", detail["coverUrl"], binary=True)
    expect(bool(cover) and cover_type.startswith("image/"), "Template cover cannot be read")

    assets = [upload(token) for _ in range(template["requiredImageCount"])]
    expect(all(a.get("id") and a.get("url") and a.get("sizeBytes") == len(PNG) for a in assets),
           "Upload metadata does not match the real PNG")
    downloaded, uploaded_type = request("GET", assets[0]["url"], token=token, binary=True)
    expect(downloaded == PNG and uploaded_type.startswith("image/"), "Uploaded file bytes changed")
    upload(token, data=b"this is not a PNG", expected=(400, 415, 422))
    upload(token, filename="smoke.txt", mime_type="text/plain", data=b"text", expected=(400, 415, 422))
    submit(token, template, [], expected=(400, 422))
    submit(token, template, assets, delay=0, expected=(400, 422))
    submit(bob["token"], template, assets, expected=(400, 403, 404, 422))
    print("PASS identity, server templates, category filter, real PNG upload and input validation")

    started = time.monotonic()
    successful = submit(token, template, assets, delay=2000)
    submission_seconds = time.monotonic() - started
    expect(submission_seconds < 4, f"Submission blocked for {submission_seconds:.2f}s")
    expect(successful["status"] in {"QUEUED", "RUNNING"},
           "202 submission must return before six seconds of phase work finishes")
    # Submit several tasks without waiting for the previous task to finish.
    failed = {stage: submit(token, template, assets, fail=stage) for stage in STAGES}
    second_success = submit(token, template, assets)
    task_ids = [successful["id"], second_success["id"]] + [task["id"] for task in failed.values()]
    expect(len(set(task_ids)) == 5, "Concurrent submissions must create independent task IDs")
    # No status reads during the first task's six seconds of work: processing must
    # continue independently, rather than advancing a phase in each GET handler.
    time.sleep(7)
    unattended = request("GET", f"/api/tasks/{successful['id']}", token=token)
    expect(unattended["status"] == "SUCCEEDED", "Task did not progress independently of polling")
    results = poll_all(token, task_ids)
    verify_terminal(results[successful["id"]], minimum_step_seconds=1.8)
    verify_terminal(results[second_success["id"]])
    for stage, task in failed.items():
        verify_terminal(results[task["id"]], stage)
    # Repeated reads after terminal state must not advance or rewrite the workflow.
    time.sleep(0.6)
    for task_id in task_ids:
        expect(request("GET", f"/api/tasks/{task_id}", token=token) == results[task_id],
               "Terminal task changed during a read-only query")
    result_bytes, result_type = request("GET", results[successful["id"]]["resultUrl"],
                                      token=token, binary=True)
    expect(bool(result_bytes) and result_type.startswith("image/"), "Result image cannot be read")
    print(f"PASS async 202 ({submission_seconds:.2f}s), concurrent tasks, three phases, all failure stages and stable terminal reads")

    history = request("GET", "/api/tasks", token=token)
    expect({task["id"] for task in history} == set(task_ids), "History lost or mixed tasks")
    creation_times = [timestamp(task["createdAt"]) for task in history]
    expect(creation_times == sorted(creation_times, reverse=True), "History must be newest first")
    request("GET", f"/api/tasks/{successful['id']}", token=bob["token"], expected=(403, 404))
    expect(request("GET", "/api/tasks", token=bob["token"]) == [], "Task history leaked between users")
    bob_assets = [upload(bob["token"]) for _ in range(template["requiredImageCount"])]
    bob_task = submit(bob["token"], template, bob_assets)
    bob_result = poll_all(bob["token"], [bob_task["id"]])[bob_task["id"]]
    verify_terminal(bob_result)
    expect({task["id"] for task in request("GET", "/api/tasks", token=bob["token"])} == {bob_task["id"]},
           "Second user history contains first user tasks")
    expect({task["id"] for task in request("GET", "/api/tasks", token=token)} == set(task_ids),
           "First user history contains second user tasks")
    print("PASS persisted history, identity switch, task access isolation and asset ownership isolation")
    print("HTTP acceptance smoke passed. Browser rendering and reload behavior need separate browser checks.")


if __name__ == "__main__":
    main()
