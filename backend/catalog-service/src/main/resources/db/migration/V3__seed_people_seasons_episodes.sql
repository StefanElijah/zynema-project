-- ══════════════════════════════════════════════════════════════════════
-- V3__seed_people_seasons_episodes.sql
--
--  * people + credits for every title that has local artwork
--  * seasons for all 24 series
--  * episode lists for the first season of six flagship series
--
-- Deterministic UUIDs: c… people · d1… seasons · e1… episodes
-- ══════════════════════════════════════════════════════════════════════

-- ────────────────────────────── people ──────────────────────────────

INSERT INTO people (id, name, slug) VALUES
    ('c0000000-0000-4000-8000-000000000001', 'Cailee Spaeny',        'cailee-spaeny'),
    ('c0000000-0000-4000-8000-000000000002', 'David Jonsson',        'david-jonsson'),
    ('c0000000-0000-4000-8000-000000000003', 'Fede Álvarez',         'fede-alvarez'),
    ('c0000000-0000-4000-8000-000000000004', 'Isabela Merced',       'isabela-merced'),
    ('c0000000-0000-4000-8000-000000000005', 'Sam Worthington',      'sam-worthington'),
    ('c0000000-0000-4000-8000-000000000006', 'Zoe Saldaña',          'zoe-saldana'),
    ('c0000000-0000-4000-8000-000000000007', 'Sigourney Weaver',     'sigourney-weaver'),
    ('c0000000-0000-4000-8000-000000000008', 'James Cameron',        'james-cameron'),
    ('c0000000-0000-4000-8000-000000000009', 'Timothée Chalamet',    'timothee-chalamet'),
    ('c0000000-0000-4000-8000-000000000010', 'Zendaya',              'zendaya'),
    ('c0000000-0000-4000-8000-000000000011', 'Rebecca Ferguson',     'rebecca-ferguson'),
    ('c0000000-0000-4000-8000-000000000012', 'Denis Villeneuve',     'denis-villeneuve'),
    ('c0000000-0000-4000-8000-000000000013', 'Oscar Isaac',          'oscar-isaac'),
    ('c0000000-0000-4000-8000-000000000014', 'Josh Hutcherson',      'josh-hutcherson'),
    ('c0000000-0000-4000-8000-000000000015', 'Elizabeth Lail',       'elizabeth-lail'),
    ('c0000000-0000-4000-8000-000000000016', 'Emma Tammi',           'emma-tammi'),
    ('c0000000-0000-4000-8000-000000000017', 'Piper Rubio',          'piper-rubio'),
    ('c0000000-0000-4000-8000-000000000018', 'Brad Pitt',            'brad-pitt'),
    ('c0000000-0000-4000-8000-000000000019', 'Damson Idris',         'damson-idris'),
    ('c0000000-0000-4000-8000-000000000020', 'Joseph Kosinski',      'joseph-kosinski'),
    ('c0000000-0000-4000-8000-000000000021', 'Matthew McConaughey',  'matthew-mcconaughey'),
    ('c0000000-0000-4000-8000-000000000022', 'Anne Hathaway',        'anne-hathaway'),
    ('c0000000-0000-4000-8000-000000000023', 'Jessica Chastain',     'jessica-chastain'),
    ('c0000000-0000-4000-8000-000000000024', 'Christopher Nolan',    'christopher-nolan'),
    ('c0000000-0000-4000-8000-000000000025', 'Elle Fanning',         'elle-fanning'),
    ('c0000000-0000-4000-8000-000000000026', 'Dan Trachtenberg',     'dan-trachtenberg'),
    ('c0000000-0000-4000-8000-000000000027', 'Dimitrius Schuster-Koloamatangi', 'dimitrius-schuster-koloamatangi'),
    ('c0000000-0000-4000-8000-000000000028', 'Ryan Gosling',         'ryan-gosling'),
    ('c0000000-0000-4000-8000-000000000029', 'Phil Lord',            'phil-lord'),
    ('c0000000-0000-4000-8000-000000000030', 'Christopher Miller',   'christopher-miller'),
    ('c0000000-0000-4000-8000-000000000031', 'Hayden Christensen',   'hayden-christensen'),
    ('c0000000-0000-4000-8000-000000000032', 'Ewan McGregor',        'ewan-mcgregor'),
    ('c0000000-0000-4000-8000-000000000033', 'Natalie Portman',      'natalie-portman'),
    ('c0000000-0000-4000-8000-000000000034', 'George Lucas',         'george-lucas'),
    ('c0000000-0000-4000-8000-000000000035', 'David Corenswet',      'david-corenswet'),
    ('c0000000-0000-4000-8000-000000000036', 'Rachel Brosnahan',     'rachel-brosnahan'),
    ('c0000000-0000-4000-8000-000000000037', 'Nicholas Hoult',       'nicholas-hoult'),
    ('c0000000-0000-4000-8000-000000000038', 'James Gunn',           'james-gunn'),
    ('c0000000-0000-4000-8000-000000000039', 'Robert Pattinson',     'robert-pattinson'),
    ('c0000000-0000-4000-8000-000000000040', 'Zoë Kravitz',          'zoe-kravitz'),
    ('c0000000-0000-4000-8000-000000000041', 'Paul Dano',            'paul-dano'),
    ('c0000000-0000-4000-8000-000000000042', 'Matt Reeves',          'matt-reeves'),
    ('c0000000-0000-4000-8000-000000000043', 'Jared Leto',           'jared-leto'),
    ('c0000000-0000-4000-8000-000000000044', 'Greta Lee',            'greta-lee'),
    ('c0000000-0000-4000-8000-000000000045', 'Joachim Rønning',      'joachim-ronning'),
    ('c0000000-0000-4000-8000-000000000046', 'Sydney Chandler',      'sydney-chandler'),
    ('c0000000-0000-4000-8000-000000000047', 'Timothy Olyphant',     'timothy-olyphant'),
    ('c0000000-0000-4000-8000-000000000048', 'Noah Hawley',          'noah-hawley'),
    ('c0000000-0000-4000-8000-000000000049', 'Hailee Steinfeld',     'hailee-steinfeld'),
    ('c0000000-0000-4000-8000-000000000050', 'Ella Purnell',         'ella-purnell'),
    ('c0000000-0000-4000-8000-000000000051', 'Kevin Alejandro',      'kevin-alejandro'),
    ('c0000000-0000-4000-8000-000000000052', 'Charlie Cox',          'charlie-cox'),
    ('c0000000-0000-4000-8000-000000000053', 'Vincent D''Onofrio',   'vincent-donofrio'),
    ('c0000000-0000-4000-8000-000000000054', 'Margarita Levieva',    'margarita-levieva'),
    ('c0000000-0000-4000-8000-000000000055', 'Michael C. Hall',      'michael-c-hall'),
    ('c0000000-0000-4000-8000-000000000056', 'Uma Thurman',          'uma-thurman'),
    ('c0000000-0000-4000-8000-000000000057', 'Peter Dinklage',       'peter-dinklage'),
    ('c0000000-0000-4000-8000-000000000058', 'Walton Goggins',       'walton-goggins'),
    ('c0000000-0000-4000-8000-000000000059', 'Aaron Moten',          'aaron-moten'),
    ('c0000000-0000-4000-8000-000000000060', 'Jared Harris',         'jared-harris'),
    ('c0000000-0000-4000-8000-000000000061', 'Lee Pace',             'lee-pace'),
    ('c0000000-0000-4000-8000-000000000062', 'Lou Llobell',          'lou-llobell'),
    ('c0000000-0000-4000-8000-000000000063', 'Steven Yeun',          'steven-yeun'),
    ('c0000000-0000-4000-8000-000000000064', 'J.K. Simmons',         'jk-simmons'),
    ('c0000000-0000-4000-8000-000000000065', 'Sandra Oh',            'sandra-oh'),
    ('c0000000-0000-4000-8000-000000000066', 'Emilia Clarke',        'emilia-clarke'),
    ('c0000000-0000-4000-8000-000000000067', 'Kit Harington',        'kit-harington'),
    ('c0000000-0000-4000-8000-000000000068', 'David Benioff',        'david-benioff'),
    ('c0000000-0000-4000-8000-000000000069', 'Sam Witwer',           'sam-witwer'),
    ('c0000000-0000-4000-8000-000000000070', 'Andrew Lincoln',       'andrew-lincoln'),
    ('c0000000-0000-4000-8000-000000000071', 'Norman Reedus',        'norman-reedus'),
    ('c0000000-0000-4000-8000-000000000072', 'Danai Gurira',         'danai-gurira');

-- ────────────────────────────── credits ─────────────────────────────

INSERT INTO credits (content_id, person_id, role, character_name, billing_order)
SELECT c.id, p.id, m.role, m.character_name, m.billing_order
FROM (VALUES
    ('alien-romulus', 'cailee-spaeny',     'ACTOR',    'Rain Carradine',        1),
    ('alien-romulus', 'david-jonsson',     'ACTOR',    'Andy',                  2),
    ('alien-romulus', 'isabela-merced',    'ACTOR',    'Kay',                   3),
    ('alien-romulus', 'fede-alvarez',      'DIRECTOR', NULL,                    1),

    ('avatar-the-way-of-water', 'sam-worthington',  'ACTOR',    'Jake Sully',    1),
    ('avatar-the-way-of-water', 'zoe-saldana',      'ACTOR',    'Neytiri',       2),
    ('avatar-the-way-of-water', 'sigourney-weaver', 'ACTOR',    'Kiri',          3),
    ('avatar-the-way-of-water', 'james-cameron',    'DIRECTOR', NULL,            1),

    ('dune-part-two', 'timothee-chalamet', 'ACTOR',    'Paul Atreides', 1),
    ('dune-part-two', 'zendaya',           'ACTOR',    'Chani',         2),
    ('dune-part-two', 'rebecca-ferguson',  'ACTOR',    'Lady Jessica',  3),
    ('dune-part-two', 'denis-villeneuve',  'DIRECTOR', NULL,            1),

    ('dune-part-one', 'timothee-chalamet', 'ACTOR',    'Paul Atreides',       1),
    ('dune-part-one', 'rebecca-ferguson',  'ACTOR',    'Lady Jessica',        2),
    ('dune-part-one', 'oscar-isaac',       'ACTOR',    'Duke Leto Atreides',  3),
    ('dune-part-one', 'denis-villeneuve',  'DIRECTOR', NULL,                  1),

    ('five-nights-at-freddys', 'josh-hutcherson', 'ACTOR',    'Mike Schmidt', 1),
    ('five-nights-at-freddys', 'elizabeth-lail',  'ACTOR',    'Vanessa',      2),
    ('five-nights-at-freddys', 'emma-tammi',      'DIRECTOR', NULL,           1),

    ('five-nights-at-freddys-2', 'josh-hutcherson', 'ACTOR',    'Mike Schmidt', 1),
    ('five-nights-at-freddys-2', 'piper-rubio',     'ACTOR',    'Abby',         2),
    ('five-nights-at-freddys-2', 'emma-tammi',      'DIRECTOR', NULL,           1),

    ('f1-the-movie', 'brad-pitt',       'ACTOR',    'Sonny Hayes',    1),
    ('f1-the-movie', 'damson-idris',    'ACTOR',    'Joshua Pearce',  2),
    ('f1-the-movie', 'joseph-kosinski', 'DIRECTOR', NULL,             1),

    ('interstellar', 'matthew-mcconaughey', 'ACTOR',    'Cooper',         1),
    ('interstellar', 'anne-hathaway',       'ACTOR',    'Amelia Brand',   2),
    ('interstellar', 'jessica-chastain',    'ACTOR',    'Murph',          3),
    ('interstellar', 'christopher-nolan',   'DIRECTOR', NULL,             1),

    ('predator-badlands', 'elle-fanning',  'ACTOR',    'Thia',  1),
    ('predator-badlands', 'dimitrius-schuster-koloamatangi', 'ACTOR', 'Dek', 2),
    ('predator-badlands', 'dan-trachtenberg', 'DIRECTOR', NULL,  1),

    ('predator-killer-of-killers', 'dan-trachtenberg', 'DIRECTOR', NULL, 1),

    ('project-hail-mary', 'ryan-gosling',       'ACTOR',    'Ryland Grace', 1),
    ('project-hail-mary', 'phil-lord',          'DIRECTOR', NULL,           1),
    ('project-hail-mary', 'christopher-miller', 'DIRECTOR', NULL,           2),

    ('star-wars-episode-iii-revenge-of-the-sith', 'hayden-christensen', 'ACTOR',    'Anakin Skywalker',   1),
    ('star-wars-episode-iii-revenge-of-the-sith', 'ewan-mcgregor',      'ACTOR',    'Obi-Wan Kenobi',     2),
    ('star-wars-episode-iii-revenge-of-the-sith', 'natalie-portman',    'ACTOR',    'Padmé Amidala',      3),
    ('star-wars-episode-iii-revenge-of-the-sith', 'george-lucas',       'DIRECTOR', NULL,                 1),

    ('superman-2025', 'david-corenswet',  'ACTOR',    'Clark Kent / Superman', 1),
    ('superman-2025', 'rachel-brosnahan', 'ACTOR',    'Lois Lane',             2),
    ('superman-2025', 'nicholas-hoult',   'ACTOR',    'Lex Luthor',            3),
    ('superman-2025', 'james-gunn',       'DIRECTOR', NULL,                    1),

    ('the-batman', 'robert-pattinson', 'ACTOR',    'Bruce Wayne / Batman', 1),
    ('the-batman', 'zoe-kravitz',      'ACTOR',    'Selina Kyle',          2),
    ('the-batman', 'paul-dano',        'ACTOR',    'The Riddler',          3),
    ('the-batman', 'matt-reeves',      'DIRECTOR', NULL,                   1),

    ('tron-ares', 'jared-leto',      'ACTOR',    'Ares',     1),
    ('tron-ares', 'greta-lee',       'ACTOR',    'Eve Kim',  2),
    ('tron-ares', 'joachim-ronning', 'DIRECTOR', NULL,       1),

    ('alien-earth', 'sydney-chandler',  'ACTOR',    'Wendy', 1),
    ('alien-earth', 'timothy-olyphant', 'ACTOR',    'Kirsh', 2),
    ('alien-earth', 'noah-hawley',      'DIRECTOR', NULL,    1),

    ('arcane', 'hailee-steinfeld', 'ACTOR', 'Vi',    1),
    ('arcane', 'ella-purnell',     'ACTOR', 'Jinx',  2),
    ('arcane', 'kevin-alejandro',  'ACTOR', 'Jayce', 3),

    ('daredevil-born-again', 'charlie-cox',       'ACTOR', 'Matt Murdock',  1),
    ('daredevil-born-again', 'vincent-donofrio',  'ACTOR', 'Wilson Fisk',   2),
    ('daredevil-born-again', 'margarita-levieva', 'ACTOR', 'Heather Glenn', 3),

    ('dexter-resurrection', 'michael-c-hall', 'ACTOR', 'Dexter Morgan', 1),
    ('dexter-resurrection', 'uma-thurman',    'ACTOR', 'Charley',       2),
    ('dexter-resurrection', 'peter-dinklage', 'ACTOR', 'Leon Prater',   3),

    ('fallout', 'ella-purnell',    'ACTOR', 'Lucy MacLean', 1),
    ('fallout', 'walton-goggins',  'ACTOR', 'The Ghoul',    2),
    ('fallout', 'aaron-moten',     'ACTOR', 'Maximus',      3),

    ('foundation', 'jared-harris', 'ACTOR', 'Hari Seldon',  1),
    ('foundation', 'lee-pace',     'ACTOR', 'Brother Day',  2),
    ('foundation', 'lou-llobell',  'ACTOR', 'Gaal Dornick', 3),

    ('invincible', 'steven-yeun',   'ACTOR', 'Mark Grayson',   1),
    ('invincible', 'jk-simmons',    'ACTOR', 'Omni-Man',       2),
    ('invincible', 'sandra-oh',     'ACTOR', 'Debbie Grayson', 3),

    ('game-of-thrones', 'emilia-clarke',  'ACTOR',    'Daenerys Targaryen', 1),
    ('game-of-thrones', 'peter-dinklage', 'ACTOR',    'Tyrion Lannister',   2),
    ('game-of-thrones', 'kit-harington',  'ACTOR',    'Jon Snow',           3),
    ('game-of-thrones', 'david-benioff',  'DIRECTOR', NULL,                 1),

    ('star-wars-maul-shadow-lord', 'sam-witwer', 'ACTOR', 'Maul', 1),

    ('the-walking-dead', 'andrew-lincoln', 'ACTOR', 'Rick Grimes',  1),
    ('the-walking-dead', 'norman-reedus',  'ACTOR', 'Daryl Dixon',  2),
    ('the-walking-dead', 'danai-gurira',   'ACTOR', 'Michonne',     3)
) AS m(content_slug, person_slug, role, character_name, billing_order)
JOIN content c ON c.slug = m.content_slug
JOIN people  p ON p.slug = m.person_slug;

-- ────────────────────────────── seasons ─────────────────────────────

INSERT INTO seasons (id, content_id, season_number, title, release_year)
SELECT ('d1000000-0000-4000-8000-' || lpad((row_number() over ())::text, 12, '0'))::uuid,
       c.id, t.season_number, 'Season ' || t.season_number, t.release_year
FROM (VALUES
    ('alien-earth', 1, 2025),
    ('arcane', 1, 2021), ('arcane', 2, 2024),
    ('daredevil-born-again', 1, 2025),
    ('dexter-resurrection', 1, 2025),
    ('fallout', 1, 2024),
    ('foundation', 1, 2021), ('foundation', 2, 2023), ('foundation', 3, 2025),
    ('invincible', 1, 2021), ('invincible', 2, 2023), ('invincible', 3, 2025),
    ('game-of-thrones', 1, 2011), ('game-of-thrones', 2, 2012), ('game-of-thrones', 3, 2013),
    ('game-of-thrones', 4, 2014), ('game-of-thrones', 5, 2015), ('game-of-thrones', 6, 2016),
    ('game-of-thrones', 7, 2017), ('game-of-thrones', 8, 2019),
    ('star-wars-maul-shadow-lord', 1, 2026),
    ('the-walking-dead', 1, 2010), ('the-walking-dead', 2, 2011), ('the-walking-dead', 3, 2012),
    ('the-walking-dead', 4, 2013), ('the-walking-dead', 5, 2014), ('the-walking-dead', 6, 2015),
    ('the-walking-dead', 7, 2016), ('the-walking-dead', 8, 2017), ('the-walking-dead', 9, 2018),
    ('the-walking-dead', 10, 2019), ('the-walking-dead', 11, 2021),
    ('breaking-bad', 1, 2008), ('breaking-bad', 2, 2009), ('breaking-bad', 3, 2010),
    ('breaking-bad', 4, 2011), ('breaking-bad', 5, 2012),
    ('stranger-things', 1, 2016), ('stranger-things', 2, 2017), ('stranger-things', 3, 2019), ('stranger-things', 4, 2022),
    ('the-last-of-us', 1, 2023), ('the-last-of-us', 2, 2025),
    ('house-of-the-dragon', 1, 2022), ('house-of-the-dragon', 2, 2024),
    ('severance', 1, 2022), ('severance', 2, 2025),
    ('the-bear', 1, 2022), ('the-bear', 2, 2023), ('the-bear', 3, 2024), ('the-bear', 4, 2025),
    ('andor', 1, 2022), ('andor', 2, 2025),
    ('the-boys', 1, 2019), ('the-boys', 2, 2020), ('the-boys', 3, 2022), ('the-boys', 4, 2024),
    ('dark', 1, 2017), ('dark', 2, 2019), ('dark', 3, 2020),
    ('chernobyl', 1, 2019),
    ('peaky-blinders', 1, 2013), ('peaky-blinders', 2, 2014), ('peaky-blinders', 3, 2016),
    ('peaky-blinders', 4, 2017), ('peaky-blinders', 5, 2019), ('peaky-blinders', 6, 2022),
    ('the-mandalorian', 1, 2019), ('the-mandalorian', 2, 2020), ('the-mandalorian', 3, 2023),
    ('westworld', 1, 2016), ('westworld', 2, 2018), ('westworld', 3, 2020), ('westworld', 4, 2022),
    ('better-call-saul', 1, 2015), ('better-call-saul', 2, 2016), ('better-call-saul', 3, 2017),
    ('better-call-saul', 4, 2018), ('better-call-saul', 5, 2020), ('better-call-saul', 6, 2022)
) AS t(content_slug, season_number, release_year)
JOIN content c ON c.slug = t.content_slug;

-- ───────────────────────────── episodes ─────────────────────────────
-- First season of six flagship series.

INSERT INTO episodes (id, season_id, episode_number, title, runtime_minutes, release_date)
SELECT ('e1000000-0000-4000-8000-' || lpad((row_number() over ())::text, 12, '0'))::uuid,
       s.id, t.episode_number, t.title, t.runtime_minutes, t.release_date::date
FROM (VALUES
    -- Arcane S1
    ('arcane', 1, 1, 'Welcome to the Playground',            42, '2021-11-06'),
    ('arcane', 1, 2, 'Some Mysteries Are Better Left Unsolved', 40, '2021-11-06'),
    ('arcane', 1, 3, 'The Base Violence Necessary for Change',  43, '2021-11-06'),
    ('arcane', 1, 4, 'Happy Progress Day!',                  39, '2021-11-13'),
    ('arcane', 1, 5, 'Everybody Wants to Be My Enemy',       40, '2021-11-13'),
    ('arcane', 1, 6, 'When These Walls Come Tumbling Down',  41, '2021-11-13'),
    ('arcane', 1, 7, 'The Boy Savior',                       40, '2021-11-20'),
    ('arcane', 1, 8, 'Oil and Water',                        41, '2021-11-20'),
    ('arcane', 1, 9, 'The Monster You Created',              44, '2021-11-20'),
    -- Fallout S1
    ('fallout', 1, 1, 'The End',     62, '2024-04-10'),
    ('fallout', 1, 2, 'The Target',  55, '2024-04-10'),
    ('fallout', 1, 3, 'The Head',    58, '2024-04-10'),
    ('fallout', 1, 4, 'The Ghouls',  60, '2024-04-10'),
    ('fallout', 1, 5, 'The Past',    57, '2024-04-10'),
    ('fallout', 1, 6, 'The Trap',    54, '2024-04-10'),
    ('fallout', 1, 7, 'The Radio',   61, '2024-04-10'),
    ('fallout', 1, 8, 'The Beginning', 63, '2024-04-10'),
    -- Game of Thrones S1
    ('game-of-thrones', 1, 1,  'Winter Is Coming',                  62, '2011-04-17'),
    ('game-of-thrones', 1, 2,  'The Kingsroad',                     56, '2011-04-24'),
    ('game-of-thrones', 1, 3,  'Lord Snow',                         58, '2011-05-01'),
    ('game-of-thrones', 1, 4,  'Cripples, Bastards, and Broken Things', 56, '2011-05-08'),
    ('game-of-thrones', 1, 5,  'The Wolf and the Lion',             55, '2011-05-15'),
    ('game-of-thrones', 1, 6,  'A Golden Crown',                    53, '2011-05-22'),
    ('game-of-thrones', 1, 7,  'You Win or You Die',                58, '2011-05-29'),
    ('game-of-thrones', 1, 8,  'The Pointy End',                    59, '2011-06-05'),
    ('game-of-thrones', 1, 9,  'Baelor',                            57, '2011-06-12'),
    ('game-of-thrones', 1, 10, 'Fire and Blood',                    53, '2011-06-19'),
    -- Breaking Bad S1
    ('breaking-bad', 1, 1, 'Pilot',                          58, '2008-01-20'),
    ('breaking-bad', 1, 2, 'Cat''s in the Bag...',            48, '2008-01-27'),
    ('breaking-bad', 1, 3, '...And the Bag''s in the River',  48, '2008-02-10'),
    ('breaking-bad', 1, 4, 'Cancer Man',                      48, '2008-02-17'),
    ('breaking-bad', 1, 5, 'Gray Matter',                     48, '2008-02-24'),
    ('breaking-bad', 1, 6, 'Crazy Handful of Nothin''',       48, '2008-03-02'),
    ('breaking-bad', 1, 7, 'A No-Rough-Stuff-Type Deal',      48, '2008-03-09'),
    -- Severance S1
    ('severance', 1, 1, 'Good News About Hell',               57, '2022-02-18'),
    ('severance', 1, 2, 'Half Loop',                          52, '2022-02-18'),
    ('severance', 1, 3, 'In Perpetuity',                      54, '2022-02-25'),
    ('severance', 1, 4, 'The You You Are',                    50, '2022-03-04'),
    ('severance', 1, 5, 'The Grim Barbarity of Optics and Design', 51, '2022-03-11'),
    ('severance', 1, 6, 'Hide and Seek',                      49, '2022-03-18'),
    ('severance', 1, 7, 'Defiant Jazz',                       48, '2022-03-25'),
    ('severance', 1, 8, 'What''s for Dinner?',                47, '2022-04-01'),
    ('severance', 1, 9, 'The We We Are',                      46, '2022-04-08'),
    -- Chernobyl
    ('chernobyl', 1, 1, '1:23:45',                        65, '2019-05-06'),
    ('chernobyl', 1, 2, 'Please Remain Calm',             65, '2019-05-13'),
    ('chernobyl', 1, 3, 'Open Wide, O Earth',             60, '2019-05-20'),
    ('chernobyl', 1, 4, 'The Happiness of All Mankind',   64, '2019-05-27'),
    ('chernobyl', 1, 5, 'Vichnaya Pamyat',                72, '2019-06-03')
) AS t(content_slug, season_number, episode_number, title, runtime_minutes, release_date)
JOIN content c ON c.slug = t.content_slug
JOIN seasons s ON s.content_id = c.id AND s.season_number = t.season_number;
