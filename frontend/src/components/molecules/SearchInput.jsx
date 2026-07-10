import { useState } from 'react';

export default function SearchInput({ onSearch }) {
  const [value, setValue] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    onSearch?.(value);
  };

  return (
    <form onSubmit={handleSubmit} className="relative">
      <input
        type="search"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Buscar..."
        aria-label="Buscar"
        className="bg-white/10 border border-white/20 text-white placeholder-white/50 text-sm rounded-md px-3 py-1.5 w-40 md:w-64 focus:outline-none focus:ring-2 focus:ring-red-600 focus:border-transparent"
      />
    </form>
  );
}
