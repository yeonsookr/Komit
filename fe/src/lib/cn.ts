import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/** Tailwind 클래스 병합 유틸 (충돌 시 뒤에 오는 클래스가 우선) */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
