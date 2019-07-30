package org.jboss.resteasy.test.spring.web.deployment.resource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

@RestController
@RequestMapping("/" + TestController.CONTROLLER_PATH)
public class TestController {

    public static final String CONTROLLER_PATH = "spring";

    @GetMapping("/hello")
    public String string(@RequestParam(name = "name") String name) {
        return "hello " + name;
    }

    @GetMapping("/hello2")
    public String stringWithDefaultParamValue(@RequestParam(name = "name", defaultValue = "world") String name) {
        return "hello " + name;
    }

    @GetMapping("/int/{num}")
    public Integer intPathVariable(@PathVariable("num") Integer number) {
        return number + 1;
    }

    @GetMapping(path = "/json/{message}")
    public SomeClass json(@PathVariable("message") String message) {
        return new SomeClass(message);
    }

    @RequestMapping(path = "/json2/{message}", produces = MediaType.APPLICATION_JSON)
    public SomeClass jsonFromRequestMapping(@PathVariable("message") String message) {
        return new SomeClass(message);
    }

    @PostMapping(path = "/json", produces = MediaType.TEXT_PLAIN, consumes = MediaType.APPLICATION_JSON)
    public String postWithJsonBody(@RequestBody SomeClass someClass) {
        return someClass.getMessage();
    }

    @RequestMapping(path = "/json2", method = RequestMethod.POST, produces = MediaType.TEXT_PLAIN, consumes = MediaType.APPLICATION_JSON)
    public String postWithJsonBodyFromRequestMapping(@RequestBody SomeClass someClass) {
        return someClass.getMessage();
    }

    @PutMapping(path = "/json3")
    public Greeting multipleInputAndJsonResponse(@RequestBody SomeClass someClass, @RequestParam(value = "suffix") String suffix) {
        return new Greeting(someClass.getMessage() + suffix);
    }

    @GetMapping(path = "/servletRequest")
    public String injectHttpServletRequest(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getRequestURL().toString();
    }
}
