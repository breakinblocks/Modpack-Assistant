package com.breakinblocks.modpackassistant.report;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class CsvWriter {
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setCommentMarker('#')
            .setRecordSeparator("\n")
            .build();

    private final StringBuilder buffer = new StringBuilder();
    private final CSVPrinter printer;

    public CsvWriter() {
        try {
            printer = new CSVPrinter(buffer, FORMAT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public CsvWriter comment(String text) {
        try {
            printer.printComment(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public CsvWriter comments(Iterable<String> lines) {
        for (String line : lines) {
            comment(line);
        }
        return this;
    }

    public CsvWriter row(Object... cells) {
        try {
            printer.printRecord(cells);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public CsvWriter row(Iterable<?> cells) {
        try {
            printer.printRecord(cells);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    public CsvWriter blank() {
        buffer.append('\n');
        return this;
    }

    public String content() {
        try {
            printer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toString();
    }
}
