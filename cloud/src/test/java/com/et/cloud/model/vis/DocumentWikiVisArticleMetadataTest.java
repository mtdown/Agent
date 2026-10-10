package com.et.cloud.model.vis;

import com.et.cloud.model.entity.DocumentWiki;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentWikiVisArticleMetadataTest {

    @Test
    void viewModelCopiesSourceUrlAndMetadataWithoutChangingCreator() throws ReflectiveOperationException {
        DocumentWiki document = new DocumentWiki();
        document.setSourceUrl("https://news.example.com/article");
        document.setMetadataJson("{\"originalAuthor\":\"李明\"}");
        document.setUserId(7L);

        DocumentWikiVis view = DocumentWikiVis.objToVis(document);

        assertEquals("https://news.example.com/article", fieldValue(view, "sourceUrl"));
        assertEquals("{\"originalAuthor\":\"李明\"}", fieldValue(view, "metadataJson"));
        assertEquals(7L, view.getUserId());
    }

    private Object fieldValue(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
