import { type LucideIcon, MessagesSquare } from "lucide-react";

export type SiteConfig = typeof siteConfig;

export type Navigation = {
  icon: LucideIcon;
  name: string;
  href: string;
};

export const siteConfig = {

};

export const navigations: Navigation[] = [
  {
    icon: MessagesSquare,
    name: "Dependency",
    href: "/dependency",
  },
];
