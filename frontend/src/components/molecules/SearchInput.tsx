import { FormEvent, useState } from 'react';
import { Input } from '../ui/input';

export default function SearchInput({ onSearch }: { onSearch?: (value: string) => void }) {
  const [value, setValue] = useState('');

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onSearch?.(value);
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
