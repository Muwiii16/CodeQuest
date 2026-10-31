-- ============================================================
-- Cosmetics become a gacha: finishing a lesson gives one random roll (common is
-- far likelier than rare, epic or legendary) instead of a fixed reward per lesson.
-- Skins become hats and badges become floating weapons.
-- ============================================================

-- 1. new category names (trails and titles keep theirs)
update cosmetics set category = 'hat' where category = 'avatar_skin';
update cosmetics set category = 'weapon' where category = 'badge';
update equipped_cosmetics set category = 'hat' where category = 'avatar_skin';
update equipped_cosmetics set category = 'weapon' where category = 'badge';

-- 2. the old per-lesson rewards: drop the ones nobody owns, keep (as gacha items) the ones a student already won
delete from cosmetics
 where unlock_type = 'stage_completion'
   and id not in (select cosmetic_id from student_cosmetics);
update cosmetics set unlock_type = 'gacha', unlock_criteria = '{}'::jsonb where unlock_type = 'stage_completion';
update cosmetics set name = 'Pencil', description = 'A trusty pencil, floating at your side.'
 where name = 'Lab Rookie';

-- 3. the starter items every student has
update cosmetics set name = 'No Hat', description = 'Hair only. Win hats from lesson rewards.' where name = 'Standard Student';
update cosmetics set name = 'Practice Wand', description = 'The plain wand every student starts with.' where name = 'Freshman';

-- 4. the gacha pool (unlock_type 'gacha'; the odds by rarity live in the game: 60 / 28 / 9 / 3 percent)
insert into cosmetics (name, category, description, rarity, unlock_type, unlock_criteria)
select v.name, v.category, v.description, v.rarity, 'gacha', '{}'::jsonb
  from (values
    ('Pencil',          'weapon', 'A trusty pencil, floating at your side.',  'common'),
    ('Ruler',           'weapon', 'Straight edges, straight answers.',        'common'),
    ('Student Cap',     'hat',    'A cap for a hard-working student.',        'common'),
    ('Paper Hat',       'hat',    'Folded from last semester''s notes.',      'common'),
    ('Green Sparks',    'trail',  'A few green sparks behind your steps.',    'common'),
    ('Bug Hunter',      'title',  'Found a bug. Fixed a bug.',                'common'),
    ('Code Blade',      'weapon', 'A blade that cuts through syntax errors.', 'rare'),
    ('Pointer Spear',   'weapon', 'Always points at the right address.',      'rare'),
    ('Headphones',      'hat',    'Lo-fi beats to debug to.',                 'rare'),
    ('Wizard Hat',      'hat',    'Casts functions without a header.',        'rare'),
    ('Aqua Trail',      'trail',  'Cool blue ripples behind you.',            'rare'),
    ('Tower Breaker',   'title',  'Towers fear this name.',                   'rare'),
    ('Debug Hammer',    'weapon', 'Smashes bugs flat.',                       'epic'),
    ('Halo',            'hat',    'A compile with zero warnings.',            'epic'),
    ('Neon Path',       'trail',  'A glowing road of light.',                 'epic'),
    ('Stack Master',    'title',  'Never overflowed once.',                   'epic'),
    ('Compiler Staff',  'weapon', 'Turns any code into victory.',             'legendary'),
    ('Golden Crown',    'hat',    'Worn by the top of the class.',            'legendary'),
    ('Golden Sparks',   'trail',  'A trail of pure gold.',                    'legendary'),
    ('Compiler King',   'title',  'Rules every build.',                       'legendary')
  ) as v(name, category, description, rarity)
 where not exists (select 1 from cosmetics c where c.name = v.name);
