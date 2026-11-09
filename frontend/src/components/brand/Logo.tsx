import { cn } from "@/utils/cn";

export function Logo({ className, tone = "light" }: { className?: string; tone?: "light" | "dark" }) {
  return (
    <span
      className={cn(
        "inline-flex h-12 w-32 shrink-0 items-center",
        tone === "dark" && "rounded-md bg-vacuo px-2",
        className,
      )}
    >
      <img
        src="/logo.png"
        alt="Ideia Space"
        className="h-full w-full object-contain"
      />
    </span>
  );
}
