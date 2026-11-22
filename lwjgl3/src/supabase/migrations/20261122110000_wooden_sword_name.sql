-- The default weapon is a wooden sword, not a wand.
update cosmetics set name = 'Wooden Sword', description = 'The plain wooden sword every student starts with.'
where name = 'Practice Wand';
