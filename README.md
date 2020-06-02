# spocode
 Spocode "spoken code" is an Intellij-plugin that enables java programmers to code by voice. It uses google speech-to-text API for voice
 recogtion. In order to be able to use the API you have to provide credentials for the plugin (see google cloud docs). After running the
 plugin an Intellij instance will open. Create a project and open a java class then press ctrl + alt + g to activate the voice recognition,
 now you are ready to code by voice commands. If you wnat to try some commands you use the commented code in the GetCommandAction. it lets
 you write commands in an input dialog. Here are some examples of commands:
 1. to seconds
 - add method integer seconds parameters integer minutes and integer hours
 - return minutes times sixty plus tree thousand six hundred times hours
 2. absolute value
 - add method integer absolute value parameters integer number
 - add if number less than zero
 - return minus one times number
 - go to line fifteen
 - return number
 3. determine maximum
 - add method integer determine maximum parameters integer first and integer second and integer third
 - add if first greater than second and and first greater than third
 - return first
 - add else if second greater than third
 - return second
 - add else
 - return third
 4. digit sum
 - add method integer digit sum parameters integer number
 - initialize integer absolute value to call absolute value parameters number
 - initialize integer count to zero
 - add while absolute value greater than zero
 - assign count to count plus absolute value modulo ten
 - assign absolute value to absolute value by ten
 - go to line forty one
 - return count
 - rename variable count to sum
 to undersand how to speak commands open the class Execute.
