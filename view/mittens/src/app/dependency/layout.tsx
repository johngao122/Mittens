import Container from "@/components/container";
import { TopNav } from "@/components/nav";

export default function DependencyLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const projectName = process.env.NEXT_PUBLIC_PROJECT_NAME || "Mittens";
  
  return (
    <>
      <TopNav title={`${projectName} | Dependency Network`} />
      <main>
        <Container>{children}</Container>
      </main>
    </>
  );
}
