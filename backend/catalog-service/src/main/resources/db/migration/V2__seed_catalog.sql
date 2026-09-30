-- ══════════════════════════════════════════════════════════════════════
-- V2__seed_catalog.sql — genres + 52 titles (28 movies, 24 series)
--
-- Deterministic UUIDs so tests and the frontend can rely on stable ids:
--   b…  genres   ·  a1… movies  ·  a2… series
-- Image paths point at the frontend's public assets (local stand-in for
-- what would be CDN URLs in production).
-- ══════════════════════════════════════════════════════════════════════

-- ────────────────────────────── genres ──────────────────────────────

INSERT INTO genres (id, name, slug) VALUES
    ('b0000000-0000-4000-8000-000000000001', 'Action',      'action'),
    ('b0000000-0000-4000-8000-000000000002', 'Adventure',   'adventure'),
    ('b0000000-0000-4000-8000-000000000003', 'Animation',   'animation'),
    ('b0000000-0000-4000-8000-000000000004', 'Comedy',      'comedy'),
    ('b0000000-0000-4000-8000-000000000005', 'Crime',       'crime'),
    ('b0000000-0000-4000-8000-000000000006', 'Documentary', 'documentary'),
    ('b0000000-0000-4000-8000-000000000007', 'Drama',       'drama'),
    ('b0000000-0000-4000-8000-000000000008', 'Fantasy',     'fantasy'),
    ('b0000000-0000-4000-8000-000000000009', 'Horror',      'horror'),
    ('b0000000-0000-4000-8000-000000000010', 'Mystery',     'mystery'),
    ('b0000000-0000-4000-8000-000000000011', 'Romance',     'romance'),
    ('b0000000-0000-4000-8000-000000000012', 'Sci-Fi',      'sci-fi'),
    ('b0000000-0000-4000-8000-000000000013', 'Thriller',    'thriller'),
    ('b0000000-0000-4000-8000-000000000014', 'War',         'war'),
    ('b0000000-0000-4000-8000-000000000015', 'Western',     'western');

-- ───────────────────────────── movies ───────────────────────────────

INSERT INTO content (id, type, title, original_title, slug, synopsis, tagline, release_year,
                     maturity_rating, runtime_minutes, poster_url, backdrop_url, average_rating,
                     popularity, metadata)
VALUES
    ('a1000000-0000-4000-8000-000000000001', 'MOVIE', 'Alien: Romulus', NULL, 'alien-romulus',
     'While scavenging the deep ends of a derelict space station, a group of young space colonizers come face to face with the most terrifying life form in the universe.',
     'In space, no one can hear you scream.', 2024, 'R', 119,
     '/assets/movies/images/alien_romulus_dark-3840x2160.jpeg',
     '/assets/movies/images/alien_romulus_dark-3840x2160.jpeg', 7.1, 87,
     '{"country": "US", "language": "en", "franchise": "Alien", "box_office_usd": 350800000}'),

    ('a1000000-0000-4000-8000-000000000002', 'MOVIE', 'Avatar: The Way of Water', NULL, 'avatar-the-way-of-water',
     'Jake Sully lives with his newfound family formed on the extrasolar moon Pandora. Once a familiar threat returns to finish what was previously started, Jake must work with Neytiri and the army of the Na''vi race to protect their home.',
     'Return to Pandora.', 2022, 'PG-13', 192,
     '/assets/movies/images/avatar_the_way_of_water_2022_avatar_2-3840x2160.jpg',
     '/assets/movies/images/avatar_the_way_of_water_2022_avatar_2-3840x2160.jpg', 7.5, 95,
     '{"country": "US", "language": "en", "franchise": "Avatar", "box_office_usd": 2320250281}'),

    ('a1000000-0000-4000-8000-000000000003', 'MOVIE', 'Dune: Part Two', NULL, 'dune-part-two',
     'Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family. Facing a choice between the love of his life and the fate of the known universe, he endeavors to prevent a terrible future only he can foresee.',
     'Long live the fighters.', 2024, 'PG-13', 166,
     '/assets/movies/images/dune_parte_dos-3840x2160.jpg',
     '/assets/movies/images/dune_parte_dos-3840x2160.jpg', 8.5, 99,
     '{"country": "US", "language": "en", "franchise": "Dune", "box_office_usd": 714444358}'),

    ('a1000000-0000-4000-8000-000000000004', 'MOVIE', 'Dune: Part One', NULL, 'dune-part-one',
     'A noble family becomes embroiled in a war for control over the galaxy''s most valuable asset while its heir becomes troubled by visions of a dark future.',
     'Beyond fear, destiny awaits.', 2021, 'PG-13', 155,
     '/assets/movies/images/dune_parte_uno-3840x2160.jpg',
     '/assets/movies/images/dune_parte_uno-3840x2160.jpg', 8.0, 93,
     '{"country": "US", "language": "en", "franchise": "Dune", "box_office_usd": 407573628}'),

    ('a1000000-0000-4000-8000-000000000005', 'MOVIE', 'Five Nights at Freddy''s', NULL, 'five-nights-at-freddys',
     'A troubled security guard begins working at Freddy Fazbear''s Pizza. During his first night on the job, he realizes that the night shift won''t be so easy to get through.',
     'Can you survive five nights?', 2023, 'PG-13', 109,
     '/assets/movies/images/five_nights_at_freddys-3840x2160.jpg',
     '/assets/movies/images/five_nights_at_freddys-3840x2160.jpg', 5.4, 78,
     '{"country": "US", "language": "en", "franchise": "Five Nights at Freddy''s", "box_office_usd": 291074183}'),

    ('a1000000-0000-4000-8000-000000000006', 'MOVIE', 'Five Nights at Freddy''s 2', NULL, 'five-nights-at-freddys-2',
     'The twisted story of Freddy Fazbear''s Pizza continues as the animatronics return with a new mystery to unravel.',
     'The nightmare continues.', 2025, 'PG-13', 104,
     '/assets/movies/images/five_nights_at_freddys_2-3840x2160.jpg',
     '/assets/movies/images/five_nights_at_freddys_2-3840x2160.jpg', 5.8, 74,
     '{"country": "US", "language": "en", "franchise": "Five Nights at Freddy''s"}'),

    ('a1000000-0000-4000-8000-000000000007', 'MOVIE', 'F1: The Movie', NULL, 'f1-the-movie',
     'A retired Formula One driver returns to the grid to mentor a young rookie while chasing one last shot at glory for a struggling team.',
     'Every second counts.', 2025, 'PG-13', 156,
     '/assets/movies/images/formula_1_brad-pitt-mercedes-3840x2160.jpg',
     '/assets/movies/images/formula_1_brad-pitt-mercedes-3840x2160.jpg', 7.9, 92,
     '{"country": "US", "language": "en", "box_office_usd": 293000000}'),

    ('a1000000-0000-4000-8000-000000000008', 'MOVIE', 'Interstellar', NULL, 'interstellar',
     'When Earth becomes uninhabitable, a farmer and ex-NASA pilot is tasked to pilot a spacecraft, along with a team of researchers, to find a new planet for humans.',
     'Mankind was born on Earth. It was never meant to die here.', 2014, 'PG-13', 169,
     '/assets/movies/images/interstellar-3840x2160.jpg',
     '/assets/movies/images/interstellar-3840x2160.jpg', 8.7, 96,
     '{"country": "US", "language": "en", "box_office_usd": 701800000, "awards": ["Oscar - Best Visual Effects"]}'),

    ('a1000000-0000-4000-8000-000000000009', 'MOVIE', 'Predator: Badlands', NULL, 'predator-badlands',
     'A young Predator outcast sets out to prove himself on a hostile planet, only to discover the hunt is not what he was taught to believe.',
     'The hunt is personal.', 2025, 'PG-13', 107,
     '/assets/movies/images/predator_badlands-3840x2160.jpg',
     '/assets/movies/images/predator_badlands-3840x2160.jpg', 7.0, 80,
     '{"country": "US", "language": "en", "franchise": "Predator"}'),

    ('a1000000-0000-4000-8000-000000000010', 'MOVIE', 'Predator: Killer of Killers', NULL, 'predator-killer-of-killers',
     'An animated anthology following three of the deadliest warriors in history as they are drawn into a hunt against the Predators.',
     'The deadliest hunters in history meet the ultimate predator.', 2025, 'R', 85,
     '/assets/movies/images/predator_killer_of_killers-3840x2160.jpg',
     '/assets/movies/images/predator_killer_of_killers-3840x2160.jpg', 7.3, 72,
     '{"country": "US", "language": "en", "franchise": "Predator"}'),

    ('a1000000-0000-4000-8000-000000000011', 'MOVIE', 'Project Hail Mary', NULL, 'project-hail-mary',
     'A lone astronaut wakes with no memory of who he is or why he is aboard a spacecraft, and must solve an impossible scientific problem to save humanity.',
     'One man. One mission. No memory.', 2026, 'PG-13', 140,
     '/assets/movies/images/project_hail_mary-3840x2160.jpg',
     '/assets/movies/images/project_hail_mary-3840x2160.jpg', 8.4, 88,
     '{"country": "US", "language": "en"}'),

    ('a1000000-0000-4000-8000-000000000012', 'MOVIE', 'Star Wars: Episode III - Revenge of the Sith', NULL, 'star-wars-episode-iii-revenge-of-the-sith',
     'Three years into the Clone Wars, the Jedi rescue Palpatine from Count Dooku. As Obi-Wan pursues a new threat, Anakin acts as a double agent between the Jedi Council and Palpatine and is lured into a sinister plan to rule the galaxy.',
     'The saga is complete.', 2005, 'PG-13', 140,
     '/assets/movies/images/star_wars_episode_3_revenge_of_the_sith-3840x2160.jpg',
     '/assets/movies/images/star_wars_episode_3_revenge_of_the_sith-3840x2160.jpg', 7.6, 89,
     '{"country": "US", "language": "en", "franchise": "Star Wars", "box_office_usd": 868390000}'),

    ('a1000000-0000-4000-8000-000000000013', 'MOVIE', 'Superman', NULL, 'superman-2025',
     'Superman must reconcile his alien heritage with his human upbringing as he faces a world that questions whether it still needs a hero.',
     'Look up.', 2025, 'PG-13', 130,
     '/assets/movies/images/superman_2025-3840x2160.jpg',
     '/assets/movies/images/superman_2025-3840x2160.jpg', 7.4, 91,
     '{"country": "US", "language": "en", "franchise": "DC Universe", "box_office_usd": 615000000}'),

    ('a1000000-0000-4000-8000-000000000014', 'MOVIE', 'The Batman', NULL, 'the-batman',
     'When a sadistic serial killer begins murdering key political figures in Gotham, Batman is forced to investigate the city''s hidden corruption and question his family''s involvement.',
     'Unmask the truth.', 2022, 'PG-13', 176,
     '/assets/movies/images/the-batman-2022-3840x2160.jpg',
     '/assets/movies/images/the-batman-2022-3840x2160.jpg', 7.8, 90,
     '{"country": "US", "language": "en", "franchise": "DC Universe", "box_office_usd": 772245583}'),

    ('a1000000-0000-4000-8000-000000000015', 'MOVIE', 'Tron: Ares', NULL, 'tron-ares',
     'A highly sophisticated program is sent from the digital world into the real world on a dangerous mission, marking humankind''s first encounter with AI beings.',
     'The world is about to change.', 2025, 'PG-13', 119,
     '/assets/movies/images/tron_ares_red-3840x2160.jpg',
     '/assets/movies/images/tron_ares_red-3840x2160.jpg', 6.6, 76,
     '{"country": "US", "language": "en", "franchise": "Tron"}'),

    ('a1000000-0000-4000-8000-000000000016', 'MOVIE', 'Inception', NULL, 'inception',
     'A thief who steals corporate secrets through the use of dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O.',
     'Your mind is the scene of the crime.', 2010, 'PG-13', 148,
     NULL, NULL, 8.8, 94,
     '{"country": "US", "language": "en", "box_office_usd": 837000000, "awards": ["Oscar - Best Cinematography"]}'),

    ('a1000000-0000-4000-8000-000000000017', 'MOVIE', 'The Dark Knight', NULL, 'the-dark-knight',
     'When the menace known as the Joker wreaks havoc and chaos on the people of Gotham, Batman must accept one of the greatest psychological and physical tests of his ability to fight injustice.',
     'Why so serious?', 2008, 'PG-13', 152,
     NULL, NULL, 9.0, 97,
     '{"country": "US", "language": "en", "franchise": "The Dark Knight Trilogy", "box_office_usd": 1006000000, "awards": ["Oscar - Best Supporting Actor"]}'),

    ('a1000000-0000-4000-8000-000000000018', 'MOVIE', 'Oppenheimer', NULL, 'oppenheimer',
     'The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.',
     'The world forever changes.', 2023, 'R', 180,
     NULL, NULL, 8.3, 93,
     '{"country": "US", "language": "en", "box_office_usd": 976000000, "awards": ["Oscar - Best Picture"]}'),

    ('a1000000-0000-4000-8000-000000000019', 'MOVIE', 'Blade Runner 2049', NULL, 'blade-runner-2049',
     'Young Blade Runner K''s discovery of a long-buried secret leads him to track down former Blade Runner Rick Deckard, who''s been missing for thirty years.',
     'There is an order to things.', 2017, 'R', 164,
     NULL, NULL, 8.0, 85,
     '{"country": "US", "language": "en", "franchise": "Blade Runner", "awards": ["Oscar - Best Cinematography"]}'),

    ('a1000000-0000-4000-8000-000000000020', 'MOVIE', 'Mad Max: Fury Road', NULL, 'mad-max-fury-road',
     'In a post-apocalyptic wasteland, a woman rebels against a tyrannical ruler in search for her homeland with the aid of a group of female prisoners, a psychotic worshipper and a drifter named Max.',
     'What a lovely day.', 2015, 'R', 120,
     NULL, NULL, 8.1, 88,
     '{"country": "AU", "language": "en", "franchise": "Mad Max", "awards": ["Oscar - Best Film Editing"]}'),

    ('a1000000-0000-4000-8000-000000000021', 'MOVIE', 'Parasite', 'Gisaengchung', 'parasite',
     'Greed and class discrimination threaten the newly formed symbiotic relationship between the wealthy Park family and the destitute Kim clan.',
     'Act like you own the place.', 2019, 'R', 132,
     NULL, NULL, 8.5, 86,
     '{"country": "KR", "language": "ko", "awards": ["Oscar - Best Picture"]}'),

    ('a1000000-0000-4000-8000-000000000022', 'MOVIE', 'Spider-Man: Across the Spider-Verse', NULL, 'spider-man-across-the-spider-verse',
     'Miles Morales catapults across the multiverse, where he encounters a team of Spider-People charged with protecting its very existence.',
     'It''s how you wear the mask that matters.', 2023, 'PG', 140,
     NULL, NULL, 8.6, 92,
     '{"country": "US", "language": "en", "franchise": "Spider-Verse"}'),

    ('a1000000-0000-4000-8000-000000000023', 'MOVIE', 'Everything Everywhere All at Once', NULL, 'everything-everywhere-all-at-once',
     'An aging Chinese immigrant is swept up in an insane adventure, in which she alone can save existence by exploring other universes and connecting with the lives she could have led.',
     'The universe is so much bigger than you realize.', 2022, 'R', 139,
     NULL, NULL, 7.8, 84,
     '{"country": "US", "language": "en", "awards": ["Oscar - Best Picture"]}'),

    ('a1000000-0000-4000-8000-000000000024', 'MOVIE', 'Sicario', NULL, 'sicario',
     'An idealistic FBI agent is enlisted by a government task force to aid in the escalating war against drugs at the border area between the U.S. and Mexico.',
     'The border is just another line to cross.', 2015, 'R', 121,
     NULL, NULL, 7.6, 79,
     '{"country": "US", "language": "en", "franchise": "Sicario"}'),

    ('a1000000-0000-4000-8000-000000000025', 'MOVIE', 'Whiplash', NULL, 'whiplash',
     'A promising young drummer enrolls at a cut-throat music conservatory where his dreams of greatness are mentored by an instructor who will stop at nothing to realize a student''s potential.',
     'The road to greatness can take you to the edge.', 2014, 'R', 106,
     NULL, NULL, 8.5, 83,
     '{"country": "US", "language": "en", "awards": ["Oscar - Best Film Editing"]}'),

    ('a1000000-0000-4000-8000-000000000026', 'MOVIE', 'Arrival', NULL, 'arrival',
     'A linguist works with the military to communicate with alien lifeforms after twelve mysterious spacecraft appear around the world.',
     'Why are they here?', 2016, 'PG-13', 116,
     NULL, NULL, 7.9, 82,
     '{"country": "US", "language": "en", "awards": ["Oscar - Best Sound Editing"]}'),

    ('a1000000-0000-4000-8000-000000000027', 'MOVIE', 'The Matrix', NULL, 'the-matrix',
     'When a beautiful stranger leads computer hacker Neo to a forbidding underworld, he discovers the shocking truth: the life he knows is the elaborate deception of an evil cyber-intelligence.',
     'Free your mind.', 1999, 'R', 136,
     NULL, NULL, 8.7, 95,
     '{"country": "US", "language": "en", "franchise": "The Matrix", "box_office_usd": 467000000}'),

    ('a1000000-0000-4000-8000-000000000028', 'MOVIE', 'Gladiator', NULL, 'gladiator',
     'A former Roman General sets out to exact vengeance against the corrupt emperor who murdered his family and sent him into slavery.',
     'What we do in life echoes in eternity.', 2000, 'R', 155,
     NULL, NULL, 8.5, 87,
     '{"country": "US", "language": "en", "franchise": "Gladiator", "awards": ["Oscar - Best Picture"]}');

-- ───────────────────────────── series ───────────────────────────────

INSERT INTO content (id, type, title, original_title, slug, synopsis, tagline, release_year,
                     maturity_rating, runtime_minutes, poster_url, backdrop_url, average_rating,
                     popularity, metadata)
VALUES
    ('a2000000-0000-4000-8000-000000000001', 'SERIES', 'Alien: Earth', NULL, 'alien-earth',
     'When a mysterious space vessel crash-lands on Earth, a young woman and a ragtag group of tactical soldiers make a fateful discovery that puts them face-to-face with the planet''s greatest threat.',
     'The hunt comes home.', 2025, 'TV-MA', NULL,
     '/assets/series/images/alien;earth-serie-card.jpg',
     '/assets/series/images/alien;earth-serie-card.jpg', 8.1, 88,
     '{"country": "US", "language": "en", "franchise": "Alien", "seasons_count": 1, "episodes_count": 8}'),

    ('a2000000-0000-4000-8000-000000000002', 'SERIES', 'Arcane', NULL, 'arcane',
     'Amid the stark discord of twin cities Piltover and Zaun, two sisters fight on rival sides of a war between magic technologies and clashing convictions.',
     'Fight for your family.', 2021, 'TV-14', NULL,
     '/assets/series/images/arcane-serie-card.jpg',
     '/assets/series/images/jinx-arcane_3840x2160.jpg', 9.0, 96,
     '{"country": "US", "language": "en", "franchise": "League of Legends", "seasons_count": 2, "episodes_count": 18}'),

    ('a2000000-0000-4000-8000-000000000003', 'SERIES', 'Daredevil: Born Again', NULL, 'daredevil-born-again',
     'Matt Murdock, a blind lawyer with heightened abilities, fights for justice through his bustling law firm, while former mob boss Wilson Fisk pursues his own political endeavors in New York.',
     'Justice is blind. Revenge is not.', 2025, 'TV-MA', NULL,
     '/assets/series/images/daredevil_born_again-serie-3840x2160.jpg',
     '/assets/series/images/daredevil_born_again-serie-3840x2160.jpg', 8.3, 85,
     '{"country": "US", "language": "en", "franchise": "Marvel", "seasons_count": 1, "episodes_count": 9}'),

    ('a2000000-0000-4000-8000-000000000004', 'SERIES', 'Dexter: Resurrection', NULL, 'dexter-resurrection',
     'Dexter Morgan awakens from a coma and discovers the world has changed, forcing him to confront old demons while a new threat closes in.',
     'The dark passenger returns.', 2025, 'TV-MA', NULL,
     '/assets/series/images/dexter;resurrection-serie-card.jpg',
     '/assets/series/images/dexter;resurrection-serie-card.jpg', 8.5, 82,
     '{"country": "US", "language": "en", "franchise": "Dexter", "seasons_count": 1, "episodes_count": 10}'),

    ('a2000000-0000-4000-8000-000000000005', 'SERIES', 'Fallout', NULL, 'fallout',
     'In a future post-apocalyptic Los Angeles, two hundred years after the apocalypse, a vault dweller leaves her home to search for her father and discovers a bizarre, violent, and highly complex world.',
     'The end of the world is just the beginning.', 2024, 'TV-MA', NULL,
     '/assets/series/images/fallout_2025-serie-3840x2160.jpg',
     '/assets/series/images/fallout_2025-serie-3840x2160.jpg', 8.5, 93,
     '{"country": "US", "language": "en", "franchise": "Fallout", "seasons_count": 1, "episodes_count": 8}'),

    ('a2000000-0000-4000-8000-000000000006', 'SERIES', 'Foundation', NULL, 'foundation',
     'A complex saga of humans scattered on planets throughout the galaxy, all living under the rule of the Galactic Empire, as a mathematician predicts its imminent collapse.',
     'All empires fall.', 2021, 'TV-14', NULL,
     '/assets/series/images/foundation-serie-3840x2160.jpg',
     '/assets/series/images/foundation-serie-3840x2160.jpg', 7.7, 80,
     '{"country": "US", "language": "en", "franchise": "Foundation", "seasons_count": 3, "episodes_count": 30}'),

    ('a2000000-0000-4000-8000-000000000007', 'SERIES', 'Invincible', NULL, 'invincible',
     'An adult animated series based on the comic book about a teenager whose father is the most powerful superhero on the planet, and who develops powers of his own.',
     'You''re not a kid anymore.', 2021, 'TV-MA', NULL,
     '/assets/series/images/invincible-serie-card.jpg',
     '/assets/series/images/invincible-serie-3840x2160.jpg', 8.7, 89,
     '{"country": "US", "language": "en", "franchise": "Invincible", "seasons_count": 3, "episodes_count": 25}'),

    ('a2000000-0000-4000-8000-000000000008', 'SERIES', 'Game of Thrones', NULL, 'game-of-thrones',
     'Nine noble families fight for control over the lands of Westeros, while an ancient enemy returns after being dormant for millennia.',
     'Winter is coming.', 2011, 'TV-MA', NULL,
     '/assets/series/images/juego_de_tronos-serie-card.jpg',
     '/assets/series/images/jon_snow-juego_de_tronos_2100x1181.jpg', 9.2, 98,
     '{"country": "US", "language": "en", "franchise": "A Song of Ice and Fire", "seasons_count": 8, "episodes_count": 73}'),

    ('a2000000-0000-4000-8000-000000000009', 'SERIES', 'Star Wars: Maul - Shadow Lord', NULL, 'star-wars-maul-shadow-lord',
     'A former Sith apprentice, long presumed lost, rises from the shadows of the underworld to build a criminal empire of his own.',
     'The shadow rises.', 2026, 'TV-14', NULL,
     '/assets/series/images/star_wars_maul_shadow_lord-serie-3840x2160.jpg',
     '/assets/series/images/star_wars_maul_shadow_lord-serie-3840x2160.jpg', 8.0, 78,
     '{"country": "US", "language": "en", "franchise": "Star Wars", "seasons_count": 1, "episodes_count": 10}'),

    ('a2000000-0000-4000-8000-000000000010', 'SERIES', 'The Walking Dead', NULL, 'the-walking-dead',
     'Sheriff Deputy Rick Grimes wakes up from a coma to learn the world is in ruins and must lead a group of survivors to stay alive.',
     'Fight the dead. Fear the living.', 2010, 'TV-MA', NULL,
     '/assets/series/images/the_walking_dead-serie-card.jpg',
     '/assets/series/images/the-walking-dead-3840x2160.jpg', 8.1, 90,
     '{"country": "US", "language": "en", "franchise": "The Walking Dead", "seasons_count": 11, "episodes_count": 177}'),

    ('a2000000-0000-4000-8000-000000000011', 'SERIES', 'Breaking Bad', NULL, 'breaking-bad',
     'A chemistry teacher diagnosed with inoperable lung cancer turns to manufacturing and selling methamphetamine with a former student to secure his family''s future.',
     'Remember my name.', 2008, 'TV-MA', NULL,
     NULL, NULL, 9.5, 99,
     '{"country": "US", "language": "en", "franchise": "Breaking Bad", "seasons_count": 5, "episodes_count": 62}'),

    ('a2000000-0000-4000-8000-000000000012', 'SERIES', 'Stranger Things', NULL, 'stranger-things',
     'When a young boy vanishes, a small town uncovers a mystery involving secret experiments, terrifying supernatural forces and one strange little girl.',
     'Every ending has a beginning.', 2016, 'TV-14', NULL,
     NULL, NULL, 8.7, 97,
     '{"country": "US", "language": "en", "franchise": "Stranger Things", "seasons_count": 4, "episodes_count": 34}'),

    ('a2000000-0000-4000-8000-000000000013', 'SERIES', 'The Last of Us', NULL, 'the-last-of-us',
     'After a global pandemic destroys civilization, a hardened survivor takes charge of a 14-year-old girl who may be humanity''s last hope.',
     'When you''re lost in the darkness, look for the light.', 2023, 'TV-MA', NULL,
     NULL, NULL, 8.7, 94,
     '{"country": "US", "language": "en", "franchise": "The Last of Us", "seasons_count": 2, "episodes_count": 16}'),

    ('a2000000-0000-4000-8000-000000000014', 'SERIES', 'House of the Dragon', NULL, 'house-of-the-dragon',
     'An internal succession war within House Targaryen at the height of its power, 172 years before the birth of Daenerys Targaryen.',
     'Fire will reign.', 2022, 'TV-MA', NULL,
     NULL, NULL, 8.4, 91,
     '{"country": "US", "language": "en", "franchise": "A Song of Ice and Fire", "seasons_count": 2, "episodes_count": 18}'),

    ('a2000000-0000-4000-8000-000000000015', 'SERIES', 'Severance', NULL, 'severance',
     'Mark leads a team of office workers whose memories have been surgically divided between their work and personal lives, until a mysterious colleague appears outside of work.',
     'Who are you at work?', 2022, 'TV-MA', NULL,
     NULL, NULL, 8.7, 90,
     '{"country": "US", "language": "en", "franchise": "Severance", "seasons_count": 2, "episodes_count": 19}'),

    ('a2000000-0000-4000-8000-000000000016', 'SERIES', 'The Bear', NULL, 'the-bear',
     'A young chef from the fine dining world returns to Chicago to run his family''s sandwich shop after a heartbreaking death.',
     'Every second counts.', 2022, 'TV-MA', NULL,
     NULL, NULL, 8.6, 86,
     '{"country": "US", "language": "en", "franchise": "The Bear", "seasons_count": 4, "episodes_count": 38}'),

    ('a2000000-0000-4000-8000-000000000017', 'SERIES', 'Andor', NULL, 'andor',
     'Prequel series to Rogue One, following the story of Cassian Andor during the formative years of the Rebellion.',
     'A rebellion is built on hope.', 2022, 'TV-14', NULL,
     NULL, NULL, 8.4, 87,
     '{"country": "US", "language": "en", "franchise": "Star Wars", "seasons_count": 2, "episodes_count": 24}'),

    ('a2000000-0000-4000-8000-000000000018', 'SERIES', 'The Boys', NULL, 'the-boys',
     'A group of vigilantes set out to take down corrupt superheroes who abuse their superpowers.',
     'Never meet your heroes.', 2019, 'TV-MA', NULL,
     NULL, NULL, 8.7, 92,
     '{"country": "US", "language": "en", "franchise": "The Boys", "seasons_count": 4, "episodes_count": 32}'),

    ('a2000000-0000-4000-8000-000000000019', 'SERIES', 'Dark', NULL, 'dark',
     'A family saga with a supernatural twist, set in a German town where the disappearance of two young children exposes the relationships among four families.',
     'Everything is connected.', 2017, 'TV-MA', NULL,
     NULL, NULL, 8.7, 84,
     '{"country": "DE", "language": "de", "seasons_count": 3, "episodes_count": 26}'),

    ('a2000000-0000-4000-8000-000000000020', 'SERIES', 'Chernobyl', NULL, 'chernobyl',
     'In April 1986, an explosion at the Chernobyl nuclear power plant in the Union of Soviet Socialist Republics becomes one of the world''s worst man-made catastrophes.',
     'What is the cost of lies?', 2019, 'TV-MA', NULL,
     NULL, NULL, 9.4, 89,
     '{"country": "US", "language": "en", "seasons_count": 1, "episodes_count": 5}'),

    ('a2000000-0000-4000-8000-000000000021', 'SERIES', 'Peaky Blinders', NULL, 'peaky-blinders',
     'A gangster family epic set in 1900s England, centering on a gang who sew razor blades in the peaks of their caps, and their fierce boss Tommy Shelby.',
     'By order of the Peaky Blinders.', 2013, 'TV-MA', NULL,
     NULL, NULL, 8.8, 88,
     '{"country": "GB", "language": "en", "seasons_count": 6, "episodes_count": 36}'),

    ('a2000000-0000-4000-8000-000000000022', 'SERIES', 'The Mandalorian', NULL, 'the-mandalorian',
     'The travels of a lone bounty hunter in the outer reaches of the galaxy, far from the authority of the New Republic.',
     'This is the way.', 2019, 'TV-14', NULL,
     NULL, NULL, 8.6, 90,
     '{"country": "US", "language": "en", "franchise": "Star Wars", "seasons_count": 3, "episodes_count": 24}'),

    ('a2000000-0000-4000-8000-000000000023', 'SERIES', 'Westworld', NULL, 'westworld',
     'At the intersection of the near future and the reimagined past, waits a world in which every human appetite can be indulged without consequence.',
     'These violent delights have violent ends.', 2016, 'TV-MA', NULL,
     NULL, NULL, 8.5, 81,
     '{"country": "US", "language": "en", "seasons_count": 4, "episodes_count": 36}'),

    ('a2000000-0000-4000-8000-000000000024', 'SERIES', 'Better Call Saul', NULL, 'better-call-saul',
     'The trials and tribulations of criminal lawyer Jimmy McGill in the years leading up to his fateful run-in with Walter White and Jesse Pinkman.',
     'Make the call.', 2015, 'TV-MA', NULL,
     NULL, NULL, 9.0, 93,
     '{"country": "US", "language": "en", "franchise": "Breaking Bad", "seasons_count": 6, "episodes_count": 63}');

-- ────────────────────────── content_genres ──────────────────────────

INSERT INTO content_genres (content_id, genre_id)
SELECT c.id, g.id
FROM (VALUES
    -- movies with local art
    ('alien-romulus', 'horror'), ('alien-romulus', 'sci-fi'), ('alien-romulus', 'thriller'),
    ('avatar-the-way-of-water', 'action'), ('avatar-the-way-of-water', 'adventure'), ('avatar-the-way-of-water', 'sci-fi'), ('avatar-the-way-of-water', 'fantasy'),
    ('dune-part-two', 'action'), ('dune-part-two', 'adventure'), ('dune-part-two', 'sci-fi'), ('dune-part-two', 'drama'),
    ('dune-part-one', 'action'), ('dune-part-one', 'adventure'), ('dune-part-one', 'sci-fi'), ('dune-part-one', 'drama'),
    ('five-nights-at-freddys', 'horror'), ('five-nights-at-freddys', 'mystery'), ('five-nights-at-freddys', 'thriller'),
    ('five-nights-at-freddys-2', 'horror'), ('five-nights-at-freddys-2', 'mystery'), ('five-nights-at-freddys-2', 'thriller'),
    ('f1-the-movie', 'drama'), ('f1-the-movie', 'action'),
    ('interstellar', 'sci-fi'), ('interstellar', 'drama'), ('interstellar', 'adventure'),
    ('predator-badlands', 'action'), ('predator-badlands', 'sci-fi'), ('predator-badlands', 'adventure'),
    ('predator-killer-of-killers', 'animation'), ('predator-killer-of-killers', 'action'), ('predator-killer-of-killers', 'sci-fi'),
    ('project-hail-mary', 'sci-fi'), ('project-hail-mary', 'adventure'), ('project-hail-mary', 'drama'),
    ('star-wars-episode-iii-revenge-of-the-sith', 'sci-fi'), ('star-wars-episode-iii-revenge-of-the-sith', 'action'), ('star-wars-episode-iii-revenge-of-the-sith', 'adventure'), ('star-wars-episode-iii-revenge-of-the-sith', 'fantasy'),
    ('superman-2025', 'action'), ('superman-2025', 'adventure'), ('superman-2025', 'sci-fi'), ('superman-2025', 'fantasy'),
    ('the-batman', 'action'), ('the-batman', 'crime'), ('the-batman', 'mystery'), ('the-batman', 'drama'),
    ('tron-ares', 'sci-fi'), ('tron-ares', 'action'), ('tron-ares', 'adventure'),
    -- movies without local art
    ('inception', 'sci-fi'), ('inception', 'action'), ('inception', 'thriller'),
    ('the-dark-knight', 'action'), ('the-dark-knight', 'crime'), ('the-dark-knight', 'drama'), ('the-dark-knight', 'thriller'),
    ('oppenheimer', 'drama'), ('oppenheimer', 'thriller'), ('oppenheimer', 'war'),
    ('blade-runner-2049', 'sci-fi'), ('blade-runner-2049', 'drama'), ('blade-runner-2049', 'mystery'),
    ('mad-max-fury-road', 'action'), ('mad-max-fury-road', 'adventure'), ('mad-max-fury-road', 'sci-fi'),
    ('parasite', 'thriller'), ('parasite', 'drama'), ('parasite', 'comedy'),
    ('spider-man-across-the-spider-verse', 'animation'), ('spider-man-across-the-spider-verse', 'action'), ('spider-man-across-the-spider-verse', 'adventure'), ('spider-man-across-the-spider-verse', 'sci-fi'),
    ('everything-everywhere-all-at-once', 'sci-fi'), ('everything-everywhere-all-at-once', 'comedy'), ('everything-everywhere-all-at-once', 'adventure'), ('everything-everywhere-all-at-once', 'drama'),
    ('sicario', 'action'), ('sicario', 'crime'), ('sicario', 'thriller'), ('sicario', 'drama'),
    ('whiplash', 'drama'),
    ('arrival', 'sci-fi'), ('arrival', 'drama'), ('arrival', 'mystery'),
    ('the-matrix', 'sci-fi'), ('the-matrix', 'action'),
    ('gladiator', 'action'), ('gladiator', 'adventure'), ('gladiator', 'drama'),
    -- series with local art
    ('alien-earth', 'sci-fi'), ('alien-earth', 'horror'), ('alien-earth', 'thriller'),
    ('arcane', 'animation'), ('arcane', 'action'), ('arcane', 'adventure'), ('arcane', 'drama'), ('arcane', 'fantasy'),
    ('daredevil-born-again', 'action'), ('daredevil-born-again', 'crime'), ('daredevil-born-again', 'drama'),
    ('dexter-resurrection', 'crime'), ('dexter-resurrection', 'drama'), ('dexter-resurrection', 'thriller'),
    ('fallout', 'sci-fi'), ('fallout', 'adventure'), ('fallout', 'drama'),
    ('foundation', 'sci-fi'), ('foundation', 'drama'), ('foundation', 'adventure'),
    ('invincible', 'animation'), ('invincible', 'action'), ('invincible', 'adventure'), ('invincible', 'sci-fi'),
    ('game-of-thrones', 'fantasy'), ('game-of-thrones', 'drama'), ('game-of-thrones', 'adventure'),
    ('star-wars-maul-shadow-lord', 'animation'), ('star-wars-maul-shadow-lord', 'action'), ('star-wars-maul-shadow-lord', 'sci-fi'),
    ('the-walking-dead', 'horror'), ('the-walking-dead', 'drama'), ('the-walking-dead', 'thriller'),
    -- series without local art
    ('breaking-bad', 'crime'), ('breaking-bad', 'drama'), ('breaking-bad', 'thriller'),
    ('stranger-things', 'sci-fi'), ('stranger-things', 'horror'), ('stranger-things', 'drama'), ('stranger-things', 'mystery'),
    ('the-last-of-us', 'drama'), ('the-last-of-us', 'horror'), ('the-last-of-us', 'adventure'),
    ('house-of-the-dragon', 'fantasy'), ('house-of-the-dragon', 'drama'), ('house-of-the-dragon', 'action'),
    ('severance', 'sci-fi'), ('severance', 'drama'), ('severance', 'mystery'), ('severance', 'thriller'),
    ('the-bear', 'drama'), ('the-bear', 'comedy'),
    ('andor', 'sci-fi'), ('andor', 'drama'), ('andor', 'thriller'), ('andor', 'action'),
    ('the-boys', 'action'), ('the-boys', 'sci-fi'), ('the-boys', 'comedy'), ('the-boys', 'drama'),
    ('dark', 'sci-fi'), ('dark', 'mystery'), ('dark', 'drama'), ('dark', 'thriller'),
    ('chernobyl', 'drama'), ('chernobyl', 'thriller'),
    ('peaky-blinders', 'crime'), ('peaky-blinders', 'drama'),
    ('the-mandalorian', 'sci-fi'), ('the-mandalorian', 'action'), ('the-mandalorian', 'adventure'),
    ('westworld', 'sci-fi'), ('westworld', 'drama'), ('westworld', 'mystery'), ('westworld', 'western'),
    ('better-call-saul', 'crime'), ('better-call-saul', 'drama')
) AS m(content_slug, genre_slug)
JOIN content c ON c.slug = m.content_slug
JOIN genres  g ON g.slug = m.genre_slug;
