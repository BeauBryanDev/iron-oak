import Link from "next/link";
import { ArrowRight } from "lucide-react";

type ShopNowButtonProps = {
  href?: string;
  label?: string;
  size?: "sm" | "md" | "lg";
  variant?: "solid" | "outline";
  className?: string;
};

const sizeStyles = {
  sm: "gap-2 px-4 py-2 text-xs",
  md: "gap-2 px-5 py-3 text-sm",
  lg: "gap-3 px-8 py-4 text-base",
};

const arrowStyles = {
  sm: "h-3.5 w-3.5",
  md: "h-4 w-4",
  lg: "h-5 w-5",
};

const variantStyles = {
  solid: "bg-orange text-black hover:bg-orange-light",
  outline:
    "border-2 border-orange text-orange hover:bg-orange hover:text-black",
};

export default function ShopNowButton({
  href = "/catalog",
  label = "SHOP NOW",
  size = "md",
  variant = "solid",
  className = "",
}: ShopNowButtonProps) {
  return (
    <Link
      href={href}
      className={`group inline-flex w-fit items-center font-black tracking-wide transition-colors ${sizeStyles[size]} ${variantStyles[variant]} ${className}`}
    >
      {label}

      <ArrowRight
        className={`${arrowStyles[size]} transition-transform group-hover:translate-x-1`}
      />
    </Link>
  );
}
