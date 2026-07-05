package com.library.dao.builder;

import java.util.ArrayList;
import java.util.List;

public class BookSearchQueryBuilder {
    private final List<String> conditions;
    private final List<Object> parameters;
    private boolean joinAuthor = false;
    private boolean joinGenre = false;
    private int offset = 0;
    private int limit = 10;

    public BookSearchQueryBuilder() {
        this.conditions = new ArrayList<>();
        this.parameters = new ArrayList<>();
    }

    public BookSearchQueryBuilder withTitle(String title) {
        if (title != null && !title.trim().isEmpty()) {
            conditions.add("b.title ILIKE ?");
            parameters.add("%" + title.trim() + "%");
        }
        return this;
    }

    public BookSearchQueryBuilder withAuthor(String author) {
        if (author != null && !author.trim().isEmpty()) {
            joinAuthor = true;
            conditions.add("(a.first_name ILIKE ? OR a.last_name ILIKE ?)");
            parameters.add("%" + author.trim() + "%");
            parameters.add("%" + author.trim() + "%");
        }
        return this;
    }

    public BookSearchQueryBuilder withGenre(String genre) {
        if (genre != null && !genre.trim().isEmpty()) {
            joinGenre = true;
            conditions.add("g.name ILIKE ?");
            parameters.add("%" + genre.trim() + "%");
        }
        return this;
    }

    public BookSearchQueryBuilder withPagination(int offset, int limit) {
        this.offset = offset;
        this.limit = limit;
        return this;
    }

    public String buildQuery() {
        StringBuilder query = new StringBuilder("SELECT DISTINCT b.id FROM books b ");

        if (joinAuthor) {
            query.append("LEFT JOIN book_authors ba ON b.id = ba.book_id ");
            query.append("LEFT JOIN authors a ON ba.author_id = a.id ");
        }
        if (joinGenre) {
            query.append("LEFT JOIN book_genres bg ON b.id = bg.book_id ");
            query.append("LEFT JOIN genres g ON bg.genre_id = g.id ");
        }

        if (!conditions.isEmpty()) {
            query.append("WHERE ");
            query.append(String.join(" AND ", conditions));
        }

        query.append(" ORDER BY b.id DESC OFFSET ? LIMIT ?");
        parameters.add(offset);
        parameters.add(limit);

        return query.toString();
    }

    public List<Object> getParameters() {
        return parameters;
    }
}
