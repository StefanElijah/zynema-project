import { Routes, Route } from 'react-router-dom';
import AppNavbar from './components/organisms/AppNavbar';
import Footer from './components/organisms/Footer';
import RequireAuth from './components/RequireAuth';
import Home from './pages/Home';
import Account from './pages/Account';
import Callback from './pages/Callback';

export default function App() {
  return (
    <div className="min-h-screen bg-black text-white flex flex-col">
      <AppNavbar />
      <main className="pt-20 flex-1">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/callback" element={<Callback />} />
          <Route
            path="/account"
            element={
              <RequireAuth>
                <Account />
              </RequireAuth>
            }
          />
        </Routes>
      </main>
      <Footer />
    </div>
  );
}
