import { Routes, Route } from 'react-router-dom';
import AppNavbar from './components/organisms/AppNavbar';
import Footer from './components/organisms/Footer';
import Home from './pages/Home';

export default function App() {
  return (
    <div className="min-h-screen bg-black text-white flex flex-col">
      <AppNavbar />
      <main className="pt-20 flex-1">
        <Routes>
          <Route path="/" element={<Home />} />
        </Routes>
      </main>
      <Footer />
    </div>
  );
}
