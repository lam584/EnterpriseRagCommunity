// src/components/ui/button.tsx
/* eslint-disable react-refresh/only-export-components */
import * as React from "react"
import { cn } from "../../lib/utils"

export type ButtonVariant = "default" | "outline" | "ghost" | "secondary"
export type ButtonSize = "default" | "sm" | "lg" | "icon"

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  asChild?: boolean
}

const sizeClasses: Record<ButtonSize, string> = {
  default: "h-9 px-4 py-2",
  sm: "h-8 rounded-md px-3",
  lg: "h-10 rounded-md px-8",
  icon: "h-9 w-9",
}

const variantClasses: Record<ButtonVariant, string> = {
  default: "bg-blue-600 text-white hover:bg-blue-700",
  outline: "border border-input bg-background hover:bg-accent hover:text-accent-foreground",
  ghost: "hover:bg-accent hover:text-accent-foreground",
  secondary: "bg-secondary text-secondary-foreground hover:bg-secondary/80",
}

export const buttonVariants = ({ size = "default", variant = "default" }: { size?: ButtonSize; variant?: ButtonVariant }) =>
  cn(
    "inline-flex items-center justify-center whitespace-nowrap rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50",
    sizeClasses[size],
    variantClasses[variant]
  )

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(({ className, variant = "default", size = "default", ...props }, ref) => {
  return (
    <button ref={ref} className={cn(buttonVariants({ size, variant }), className)} {...props} />
  )
})
Button.displayName = "Button"

export { Button }
