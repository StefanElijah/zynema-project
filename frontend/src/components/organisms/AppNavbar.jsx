import { Link } from 'react-router-dom';
import SearchInput from '../molecules/SearchInput';

export default function AppNavbar() {
  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-black/90 backdrop-blur-sm border-b border-white/10">
      <div className="flex items-center justify-between px-4 md:px-8 py-3">
        <Link to="/" className="text-red-600 font-bold text-2xl tracking-tight">
          Zynema
        </Link>

        <nav className="hidden md:flex items-center gap-6 text-sm text-white/80">
          <Link to="/" className="hover:text-white transition">Inicio</Link>
          <a href="#series" className="hover:text-white transition">Series</a>
          <a href="#movies" className="hover:text-white transition">Películas</a>
          <a href="#my-list" className="hover:text-white transition">Mi Lista</a>
        </nav>

        <div className="flex items-center gap-3">
          <SearchInput />
        </div>
      </div>
    </header>
  );
}
