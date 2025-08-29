import type { Metadata } from "next";
import { Gabarito } from "next/font/google";
import { siteConfig } from "@/config/site";
import { cn } from "@/lib/utils";
import "@/style/globals.css";
import { Providers } from "./providers";

const gabarito = Gabarito({ subsets: ["latin"], variable: "--font-gabarito" });

export const metadata: Metadata = {
  title: siteConfig.title,
  description: siteConfig.description,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const projectName = process.env.NEXT_PUBLIC_PROJECT_NAME || siteConfig.title;
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={cn("bg-background font-sans", gabarito.variable)}>
        <Providers>
          <div className="min-h-[100dvh] flex flex-col">
            <header className="sticky top-0 z-30 border-b border-border bg-slate-900/70 backdrop-blur supports-[backdrop-filter]:bg-slate-900/50">
              <div className="mx-auto w-full max-w-8xl px-6 tablet:px-10 desktop:px-14 py-4">
                <h1 className="text-xl font-semibold text-white">{projectName}</h1>
              </div>
            </header>
            <main className="flex-1 overflow-auto">
              {children}
            </main>
          </div>
        </Providers>
      </body>
    </html>
  );
}
