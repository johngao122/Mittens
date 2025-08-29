"use client";

import Container from "../container";
import { ThemeToggle } from "../theme-toggle";

export default function TopNav({ title }: { title: string }) {
  return (
    <Container className="flex h-16 items-center justify-between border-b border-border">
      <h1 className="text-2xl font-medium text-black dark:text-white">{title}</h1>
      <ThemeToggle />
    </Container>
  );
}
