import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";
import { cn } from "@/utils/cn";

interface Shared {
  label: string;
  error?: string | null;
  hint?: string;
  /** conteúdo à direita dentro do campo (ex.: botão de mostrar senha) */
  trailing?: ReactNode;
}

const controlBase =
  "rounded border bg-white px-3.5 text-ink placeholder:text-ink-mute transition-colors " +
  "hover:border-estrato/60 focus:border-estrato focus:outline-none focus:ring-2 focus:ring-ciano/60 " +
  "disabled:cursor-not-allowed disabled:bg-estelar disabled:text-ink-soft";

function Wrapper({
  id,
  label,
  error,
  hint,
  children,
  required,
}: Shared & { id: string; children: ReactNode; required?: boolean }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-[13px] font-semibold text-ink">
        {label}
        {required && (
          <span className="text-acao" aria-hidden>
            {" "}
            *
          </span>
        )}
      </label>
      {children}
      {error ? (
        <p id={`${id}-err`} role="alert" className="text-[13px] font-medium text-danger">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-[13px] text-ink-soft">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

export const TextField = forwardRef<HTMLInputElement, Shared & InputHTMLAttributes<HTMLInputElement>>(
  function TextField({ label, error, hint, trailing, className, id, required, ...rest }, ref) {
    const auto = useId();
    const fid = id ?? auto;
    return (
      <Wrapper id={fid} label={label} error={error} hint={hint} required={required}>
        <div className="relative">
          <input
            ref={ref}
            id={fid}
            required={required}
            aria-invalid={!!error || undefined}
            aria-describedby={error ? `${fid}-err` : hint ? `${fid}-hint` : undefined}
            className={cn(
              controlBase,
              "w-full h-11 text-[15px]",
              error ? "border-danger" : "border-line-strong",
              trailing ? "pr-11" : undefined,
              className,
            )}
            {...rest}
          />
          {trailing && <div className="absolute inset-y-0 right-1 flex items-center">{trailing}</div>}
        </div>
      </Wrapper>
    );
  },
);

export const SelectField = forwardRef<
  HTMLSelectElement,
  Shared & SelectHTMLAttributes<HTMLSelectElement>
>(function SelectField({ label, error, hint, className, id, required, children, ...rest }, ref) {
  const auto = useId();
  const fid = id ?? auto;
  return (
    <Wrapper id={fid} label={label} error={error} hint={hint} required={required}>
      <select
        ref={ref}
        id={fid}
        required={required}
        aria-invalid={!!error || undefined}
        aria-describedby={error ? `${fid}-err` : undefined}
        className={cn(controlBase, "w-full h-11 text-[15px]", error ? "border-danger" : "border-line-strong", className)}
        {...rest}
      >
        {children}
      </select>
    </Wrapper>
  );
});

export const TextAreaField = forwardRef<
  HTMLTextAreaElement,
  Shared & TextareaHTMLAttributes<HTMLTextAreaElement>
>(function TextAreaField({ label, error, hint, className, id, required, ...rest }, ref) {
  const auto = useId();
  const fid = id ?? auto;
  return (
    <Wrapper id={fid} label={label} error={error} hint={hint} required={required}>
      <textarea
        ref={ref}
        id={fid}
        required={required}
        aria-invalid={!!error || undefined}
        aria-describedby={error ? `${fid}-err` : hint ? `${fid}-hint` : undefined}
        className={cn(controlBase, "w-full min-h-24 py-2.5 text-[15px]", error ? "border-danger" : "border-line-strong", className)}
        {...rest}
      />
    </Wrapper>
  );
});

/** Campo compacto para barras de filtro (rótulo visualmente oculto). */
export function FilterSelect({
  label,
  className,
  children,
  ...rest
}: { label: string } & SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      aria-label={label}
      className={cn(controlBase, "h-[34px] w-auto min-w-0 border-line-strong pr-8 text-[13px] font-semibold text-estrato", className)}
      {...rest}
    >
      {children}
    </select>
  );
}
