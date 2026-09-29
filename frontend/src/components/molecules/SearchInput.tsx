import { FormEvent, useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Input } from '../ui/input';

export default function SearchInput({ onSearch }: { onSearch?: (value: string) => void }) {
  const [value, setValue] = useState('');
  const location = useLocation();

  // The search page owns the committed query in the URL; the box mirrors it
  // when arriving from somewhere else (a card, the back button).
  useEffect(() => {
    if (location.pathname === '/search') {
      setValue(new URLSearchParams(location.search).get('q') ?? '');
    }
  }, [location.pathname, location.search]);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (value.trim()) {
      onSearch?.(value.trim());
    }
  };

  return (
    <form onSubmit={handleSubmit} className="relative">
      <Input
        type="search"
        value={value}
        onChange={(event) => setValue(event.target.value)}
        placeholder="Buscar..."
        aria-label="Buscar"
        className="w-40 md:w-64"
      />
    </form>
  );
}
