package com.ms.test.app;

import com.ms.test.console.ConsolePrinter;
import com.ms.test.datetime.current.CurrentDateTime;

public class PrintTimestamp {
    public static void main(String[] args) {
        ConsolePrinter.println(CurrentDateTime.asMessage());
    }
}
