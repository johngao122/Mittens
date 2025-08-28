import Container from "@/components/container";
import { TopNav } from "@/components/nav";

export default function DependencyLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <>
      <TopNav title="Dependency" />
      <main>
        <Container>{children}</Container>
      </main>
    </>
  );
}
