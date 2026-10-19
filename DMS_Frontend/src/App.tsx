import { BrowserRouter, Link, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import type { CSSProperties } from 'react'
import { FilesIcon, FolderSimpleIcon } from '@phosphor-icons/react'

import Collections from './pages/Collections'
import Dashboard from './pages/Dashboard'
import DocumentDetail from './pages/DocumentDetail'
import ThemeToggle from './components/ThemeToggle'
import { Separator } from '@/components/ui/separator'
import { Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent, SidebarHeader, SidebarInset, SidebarMenu, SidebarMenuButton, SidebarMenuItem, SidebarProvider, SidebarRail, SidebarTrigger } from '@/components/ui/sidebar'
import { Toaster } from '@/components/ui/sonner'
import { TooltipProvider } from '@/components/ui/tooltip'

const NAV_ITEMS = [
  {
    to: '/',
    label: 'Documents',
    icon: FilesIcon,
    isActive: (pathname: string) => pathname === '/' || pathname.startsWith('/documents'),
  },
  {
    to: '/collections',
    label: 'Collections',
    icon: FolderSimpleIcon,
    isActive: (pathname: string) => pathname.startsWith('/collections'),
  },
]

function sectionLabel(pathname: string): string {
  if (pathname.startsWith('/collections')) return 'Collections'
  if (pathname.startsWith('/documents/')) return 'Document'
  return 'Documents'
}

function AppShell() {
  const location = useLocation()

  return (
    <SidebarProvider style={{ '--sidebar-width': '18rem' } as CSSProperties}>
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton size="lg" asChild className="h-14 [&_svg]:size-5">
                <Link to="/">
                  <div className="flex aspect-square size-10 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
                    <FilesIcon />
                  </div>
                  <div className="grid flex-1 text-left leading-tight">
                    <span className="truncate text-base font-semibold">DMS</span>
                    <span className="truncate text-sm text-muted-foreground">Document archive</span>
                  </div>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarHeader>

        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupContent>
              <SidebarMenu className="gap-1.5">
                {NAV_ITEMS.map((item) => (
                  <SidebarMenuItem key={item.to}>
                    <SidebarMenuButton
                      asChild
                      isActive={item.isActive(location.pathname)}
                      tooltip={item.label}
                      className="h-10 text-base [&_svg]:size-5"
                    >
                      <NavLink to={item.to} end={item.to === '/'}>
                        <item.icon />
                        <span>{item.label}</span>
                      </NavLink>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>

        <SidebarFooter>
          <SidebarMenu className="gap-1.5">
            <SidebarMenuItem>
              <ThemeToggle />
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
        <SidebarRail />
      </Sidebar>

      <SidebarInset>
        <header className="flex h-12 shrink-0 items-center gap-2 border-b px-4">
          <SidebarTrigger className="-ml-1" />
          <Separator orientation="vertical" className="mr-1 h-4" />
          <span className="text-sm text-muted-foreground">{sectionLabel(location.pathname)}</span>
        </header>
        <div className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/documents/:id" element={<DocumentDetail />} />
            <Route path="/collections" element={<Collections />} />
          </Routes>
        </div>
      </SidebarInset>

      <Toaster position="bottom-right" />
    </SidebarProvider>
  )
}

function App() {
  return (
    <BrowserRouter>
      <TooltipProvider>
        <AppShell />
      </TooltipProvider>
    </BrowserRouter>
  )
}

export default App
