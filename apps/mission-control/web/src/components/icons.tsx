/** Decorative icons from the mockup. All are hidden from screen readers; the text next to them carries the meaning. */

import type {ReactNode} from 'react';

interface IconProps {
  size?: number;
  strokeWidth?: number;
}

function Svg({
  size = 18,
  strokeWidth = 2,
  children,
}: IconProps & {children: ReactNode}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export function LogoIcon() {
  return (
    <svg width="30" height="30" viewBox="0 0 30 30" aria-hidden="true">
      <rect x="1" y="1" width="28" height="28" rx="7" fill="#2F6FED" />
      <path
        d="M8 20 L12 10 L15 16 L18 10 L22 20"
        fill="none"
        stroke="#FFFFFF"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function OverviewIcon() {
  return (
    <Svg>
      <rect x="3" y="3" width="7" height="9" rx="1.5" />
      <rect x="14" y="3" width="7" height="5" rx="1.5" />
      <rect x="14" y="12" width="7" height="9" rx="1.5" />
      <rect x="3" y="16" width="7" height="5" rx="1.5" />
    </Svg>
  );
}

export function CodeIcon() {
  return (
    <Svg>
      <path d="M8 7 L3 12 L8 17" />
      <path d="M16 7 L21 12 L16 17" />
      <path d="M14 4 L10 20" />
    </Svg>
  );
}

export function DatabaseIcon() {
  return (
    <Svg>
      <ellipse cx="12" cy="5" rx="8" ry="3" />
      <path d="M4 5 V12 C4 13.7 7.6 15 12 15 C16.4 15 20 13.7 20 12 V5" />
      <path d="M4 12 V19 C4 20.7 7.6 22 12 22 C16.4 22 20 20.7 20 19 V12" />
    </Svg>
  );
}

export function DocumentIcon() {
  return (
    <Svg>
      <path d="M6 3 H14 L19 8 V21 H6 Z" />
      <path d="M14 3 V8 H19" />
      <path d="M9 13 H16" />
      <path d="M9 17 H16" />
    </Svg>
  );
}

export function SearchIcon() {
  return (
    <Svg size={16}>
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20 L16 16" />
    </Svg>
  );
}

export function CheckIcon({size = 16, strokeWidth = 2.4}: IconProps) {
  return (
    <Svg size={size} strokeWidth={strokeWidth}>
      <path d="M5 12 L10 17 L19 7" />
    </Svg>
  );
}

export function ClockIcon() {
  return (
    <Svg size={16} strokeWidth={2.4}>
      <circle cx="12" cy="12" r="8" />
      <path d="M12 8 V12 L15 14" />
    </Svg>
  );
}

export function BlockedIcon() {
  return (
    <Svg size={16} strokeWidth={2.4}>
      <circle cx="12" cy="12" r="8" />
      <path d="M7 7 L17 17" />
    </Svg>
  );
}

export function TrendIcon() {
  return (
    <Svg size={20} strokeWidth={2.4}>
      <path d="M3 17 L9 11 L13 15 L21 7" />
      <path d="M15 7 H21 V13" />
    </Svg>
  );
}

export function SparkleIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M12 3 L14 10 L21 12 L14 14 L12 21 L10 14 L3 12 L10 10 Z" />
    </svg>
  );
}
