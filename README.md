ClickScript
===========

ClickScript is a small library of functions, that let you write Gatling scripts the way you'd write Selenium scripts.
It handles routine tasks like filling in pre-filled values on forms, and figuring out where to submit forms, freeing you
up to write scripts.

Here's an example:

    import io.gatling.core.Predef._
    import io.gatling.http.Predef._
    import io.github.clickscript.Predef._

    object MyScenario {
      scenario("MyScenario")
        .exec(
          clickStep("Go To Homepage")
            .goTo("/index.html")
            // It's a thin wrapper around Gatling's HTTP support, so use checks as you would normally
            .check(css("h1") is "Welcome to our site")
        )
        .exec(
          clickStep("Click Log In")
            .click("#log-in") // CSS Selectors
        )
        .exec(
          clickStep("Enter Log-in Details")
            .form // or, if there's more than one form, .form("#login-form")
            .enterField("username", "${userName}")
            .enterField("password", "${password}")
            .tickCheckbox("remember-me")
            .post // or, if the submit button does something special, .clickPostButton("#submit-button")
        )
        .exec(exitBrowser) // Clear data about the current page - may save you memory
    }

So, what's the catch? It'll use more memory than a well-optimised, manually correlated script. And it doesn't know
JavaScript, so you'll have to handle AJAX calls manually.

How do you use it? If you're using Gatling with the Maven plugin, and using Gatling 2.0.0-RC1, just add the following to the dependencies in your POM:

    <dependency>
        <groupId>io.github.clickscript</groupId>
        <artifactId>clickscript_2.10</artifactId>
        <version>0.1</version>
        <scope>test</scope>
    </dependency>

