package com.thebund1st.paa.command.interceptor;

import com.thebund1st.daming.application.interceptor.CommandHandler;

public class CommandHandlerWithoutReturn {

    @CommandHandler
    public void handle(AnotherCommand command) {

    }


}
