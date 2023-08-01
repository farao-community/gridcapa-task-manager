package com.farao_community.farao.gridcapa.task_manager.app.entities;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessFileMinioTest {

    @Test
    void hasSameTypeAndValidityEqualsTest() {
        ProcessFile processFile1 = new ProcessFile(
                "cgm-name",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile2 = new ProcessFile(
                "cgm-name2",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        List<ProcessFileMinio> fileMinioList = new ArrayList<>();
        ProcessFileMinio file1 = new ProcessFileMinio(processFile1, null);
        ProcessFileMinio file2 = new ProcessFileMinio(processFile2, null);
        assertTrue(file1.hasSameTypeAndValidity(file1));
        assertTrue(file2.hasSameTypeAndValidity(file2));
        assertTrue(file1.hasSameTypeAndValidity(file2));
        assertTrue(file2.hasSameTypeAndValidity(file1));
    }

    @Test
    void hasSameTypeAndValidityDifferentTest() {
        ProcessFile processFile1 = new ProcessFile(
                "cgm-name",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile2 = new ProcessFile(
                "cgm-name2",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:01Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile3 = new ProcessFile(
                "cgm-name2",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:02Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile4 = new ProcessFile(
                "cgm-name2",
                "input",
                "CRAC",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        List<ProcessFileMinio> fileMinioList = new ArrayList<>();
        ProcessFileMinio file1 = new ProcessFileMinio(processFile1, null);
        ProcessFileMinio file2 = new ProcessFileMinio(processFile2, null);
        ProcessFileMinio file3 = new ProcessFileMinio(processFile3, null);
        ProcessFileMinio file4 = new ProcessFileMinio(processFile4, null);
        //diff start time
        assertFalse(file1.hasSameTypeAndValidity(file2));
        //diff end time
        assertFalse(file1.hasSameTypeAndValidity(file3));
        //diff file type
        assertFalse(file1.hasSameTypeAndValidity(file4));
    }
}
