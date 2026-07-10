export default function HeroSection() {
  return (
    <section className="py-5">
      <div className="container mx-auto px-4">
        <h1 className="text-4xl md:text-5xl font-bold mb-3">Arcane</h1>
        <p className="md:w-1/2 text-white/80 mb-4">
          Serie animada ambientada en el universo de League of Legends.
        </p>
        <div className="flex gap-2">
          <button className="bg-red-600 hover:bg-red-700 text-white font-semibold px-5 py-2 rounded transition">
            Reproducir
          </button>
          <button className="bg-white/20 hover:bg-white/30 text-white font-semibold px-5 py-2 rounded transition">
            Más Info
          </button>
        </div>
      </div>
    </section>
  );
}
