INSERT INTO wiki_space (type, name, ownerUserId)
SELECT 0, CONCAT('个人区-', u.id), u.id
FROM user u
LEFT JOIN wiki_space s ON s.type = 0 AND s.ownerUserId = u.id AND s.isDelete = 0
WHERE u.isDelete = 0 AND s.id IS NULL;

UPDATE document_wiki d
JOIN wiki_space s ON s.type = 0 AND s.ownerUserId = d.userId AND s.isDelete = 0
SET d.spaceId = s.id, d.folderId = NULL
WHERE d.spaceId IS NULL;
