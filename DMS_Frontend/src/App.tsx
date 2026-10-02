import { BrowserRouter, Link, NavLink, Route, Routes } from 'react-router-dom';
import Collections from './pages/Collections';
import Dashboard from './pages/Dashboard';
import DocumentDetail from './pages/DocumentDetail';
import Tags from './pages/Tags';

function App() {
  return (
    <BrowserRouter>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand">
            <span className="brand-mark">🗂️</span>
            DMS <small>paperless</small>
          </Link>
          <nav className="nav">
            <NavLink to="/" end className={({ isActive }) => (isActive ? 'active' : '')}>
              Documents
            </NavLink>
            <NavLink
              to="/collections"
              className={({ isActive }) => (isActive ? 'active' : '')}
            >
              Collections
            </NavLink>
            <NavLink to="/tags" className={({ isActive }) => (isActive ? 'active' : '')}>
              Tags
            </NavLink>
          </nav>
        </div>
      </header>
      <main className="container">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/documents/:id" element={<DocumentDetail />} />
          <Route path="/collections" element={<Collections />} />
          <Route path="/tags" element={<Tags />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}

export default App;
