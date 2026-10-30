package com.xiaoying.workshop;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final IdentityService identities;
    private final WorkshopMapper mapper;
    private final AssetService assets;
    private final TaskService tasks;
    public ApiController(IdentityService identities, WorkshopMapper mapper, AssetService assets, TaskService tasks) {
        this.identities = identities; this.mapper = mapper; this.assets = assets; this.tasks = tasks;
    }
    @PostMapping("/identities") @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.IdentityResponse createIdentity(@RequestBody @Valid ApiDtos.IdentityRequest request) {
        return identities.create(request.displayName());
    }
    @GetMapping("/me")
    public ApiDtos.Me me(@RequestHeader(value = "Authorization", required = false) String auth) {
        Models.Identity identity = identities.authenticate(auth);
        return new ApiDtos.Me(identity.id, identity.displayName);
    }
    @GetMapping("/templates")
    public List<ApiDtos.TemplateResponse> templates(@RequestParam(required = false) String category) {
        return mapper.templates(category).stream().map(ApiDtos.TemplateResponse::from).toList();
    }
    @GetMapping("/templates/{id}")
    public ApiDtos.TemplateResponse template(@PathVariable String id) {
        Models.Template template = mapper.template(id);
        if (template == null) throw new ApiException(404, "模板不存在");
        return ApiDtos.TemplateResponse.from(template);
    }
    @PostMapping("/assets") @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.AssetResponse upload(@RequestHeader(value = "Authorization", required = false) String auth,
            @RequestPart("file") MultipartFile file) {
        return assets.upload(identities.authenticate(auth).id, file);
    }
    @PostMapping("/tasks") @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiDtos.TaskResponse submit(@RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody @Valid ApiDtos.TaskRequest request) {
        return tasks.submit(identities.authenticate(auth).id, request);
    }
    @GetMapping("/tasks")
    public List<ApiDtos.TaskResponse> list(@RequestHeader(value = "Authorization", required = false) String auth) {
        return tasks.list(identities.authenticate(auth).id);
    }
    @GetMapping("/tasks/{id}")
    public ApiDtos.TaskResponse task(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) {
        return tasks.get(identities.authenticate(auth).id, id);
    }
}
