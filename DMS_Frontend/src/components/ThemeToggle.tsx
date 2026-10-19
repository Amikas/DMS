import { MoonIcon, SunIcon } from '@phosphor-icons/react'
import { useTheme } from 'next-themes'

import { SidebarMenuButton } from '@/components/ui/sidebar'

export default function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme()
  const isDark = resolvedTheme !== 'light'

  return (
    <SidebarMenuButton
      tooltip={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
      className="h-10 text-base [&_svg]:size-5"
      onClick={() => setTheme(isDark ? 'light' : 'dark')}
    >
      {isDark ? <SunIcon /> : <MoonIcon />}
      <span>{isDark ? 'Light mode' : 'Dark mode'}</span>
    </SidebarMenuButton>
  )
}
