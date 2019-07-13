# Setup
## Play
    
    sbt run
    
## Camunda

Make sure that Camunda is running on port 8080. I use there Docker Image.

Deploy the Process `example-process.bpmn`: http://localhost:8080/camunda/app/cockpit/default/#/repository

## Telegram

Setup the Bot like described here: https://github.com/pme123/play-scala-telegrambot4s#setup-the-bot 

Create a Group in your Telegram Client. Add as many colleagues as you like. 

Add the commands to your newly created bot:
```
register - Registers you to the Bot.
mytasks - My pending Tasks.
``` 
Open the chat with your Bot and type `\register`.

This will register you to the application so the id is known by the app.

## Run the Process 

Start the Process _Telegram Integration_: http://localhost:8080/camunda/app/tasklist/default/#/

Now you should receive a message in your group. Check the BPMN to see how the process works.