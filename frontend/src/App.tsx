import { Routes, Route } from 'react-router-dom';
import AppNavbar from './components/organisms/AppNavbar';
import Footer from './components/organisms/Footer';
import RequireAuth from './components/RequireAuth';
import Home from './pages/Home';
import Browse from './pages/Browse';
import Search from './pages/Search';
import Detail from './pages/Detail';
import Plans from './pages/Plans';
import MyList from './pages/MyList';
import Account from './pages/Account';
import Callback from './pages/Callback';
import Watch from './pages/Watch';

export default function App() {
  return (
    <div className="min-h-screen bg-black text-white flex flex-col">
      <AppNavbar />
      <main className="pt-20 flex-1">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/catalog" element={<Browse />} />
          <Route path="/search" element={<Search />} />
          <Route path="/title/:idOrSlug" element={<Detail />} />
          <Route path="/plans" element={<Plans />} />
          <Route path="/callback" element={<Callback />} />
          <Route
            path="/my-list"
            element={
              <RequireAuth>
                <MyList />
              </RequireAuth>
            }
          />
          <Route
            path="/account"
            element={
              <RequireAuth>
                <Account />
              </RequireAuth>
            }
          />
          {/* Watching needs an account and an active plan; the page shows the
              paywall, and playback-service enforces it. */}
          <Route
            path="/watch/:contentId"
            element={
              <RequireAuth>
                <Watch />
              </RequireAuth>
            }
          />
        </Routes>
      </main>
      <Footer />
    </div>
  );
}
