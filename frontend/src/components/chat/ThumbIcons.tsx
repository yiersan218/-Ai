// 实心点赞/点踩图标：袖口与手掌是两块独立填充 中间留缝 用于"已选中"态
// （lucide 只有描边版 填充后缝隙会与填充同色而消失 故单独用实心几何）

interface ThumbIconProps {
  className?: string;
}

export function ThumbUpFilledIcon({ className }: ThumbIconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      {/* 袖口（左下独立小块） */}
      <path d="M1 21h4V9H1z" />
      {/* 手掌与手指 与袖口间留出 x5→x7 的缝 */}
      <path d="M23 10c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73z" />
    </svg>
  );
}

export function ThumbDownFilledIcon({ className }: ThumbIconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      {/* 袖口（右上独立小块） */}
      <path d="M19 3h4v12h-4z" />
      {/* 手掌与手指 与袖口间留缝 */}
      <path d="M15 3H6c-.83 0-1.54.5-1.84 1.22l-3.02 7.05c-.09.23-.14.47-.14.73v2c0 1.1.9 2 2 2h6.31l-.95 4.57-.03.32c0 .41.17.79.44 1.06L9.83 23l6.59-6.59c.36-.36.58-.86.58-1.41V5c0-1.1-.9-2-2-2z" />
    </svg>
  );
}
