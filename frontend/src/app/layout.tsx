import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = {
  title: "映作 Studio · 把灵感变成作品",
  description: "选择风格，上传素材，在映作 Studio 开始你的下一件作品。",
};
export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
