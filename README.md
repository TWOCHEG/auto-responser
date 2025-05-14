<div align="center">

<img src="src/main/resources/assets/responser/openrouter_icon.png" alt="openrouter.ai logo" width="200" height="200">

# Auto Responser
AI auto responser, minecraft mod (fabric only)

</div>

> the service in use may be free of charge

## to start

1. get api key on [openrouter.ai](https://openrouter.ai/settings/keys) and set in mod settings
2. **(unessentially)** select a model on [free models list](https://openrouter.ai/models?max_price=0) `and set in mod settings`

## how does it work?

1. receives messages from the chat
2. if the message contains [one of your nickname variations](src/client/java/com/responser/utils/MentionChecker.java) the code will start the waiting process for the selected time
3. if you haven't canceled the process in any way during this time, a request request will be sent to the API and the response on your behalf will be sent to the chat

enjoy using it :)

[join discord server](https://discord.gg/4eVSEj9jku)