package services.hello

import javax.jws.WebService

import services.Greeter

@WebService(endpointInterface = "services.Greeter")
class HelloWorldImpl extends Greeter {

    def greetMe(requestType: String): String = {
        "Hello " + requestType
    }

    def sayHi(): String = {
        "Hello " + hello.BuildInfo.toString
    }
}
