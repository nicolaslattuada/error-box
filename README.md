# The Error-box™
Allows to declare a type that handles json deserialization errors gracefully

Kotlin adaptation of a swift implementation from [MP0w](https://github.com/MP0w) 

## What does it do
Given the following data classes
```kotlin
    data class Person(val name: String, val city: String)
    data class Team(val persons: List<ErrorBox<Person>>)
```
And a potentially broken json 
```json
{
  "persons": [
    {"name":  "Nicolas", "city":  "Barcelona"},
    {"name":  "Alex"}
  ]
}
```
You would normally get a deserialization error, 
but with error box you get a list of error boxes that either contain the value or the error message. 

```kotlin
val team: Team = objectMapper.readValue(teamJson)
println(team.persons)
```
:dizzy: :sparkles:
```text
[Right(b=Person(name=Nicolas, city=Barcelona)), Left(a=com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException: Instantiation of [simple type, class com.nicolattu.ErrorBoxTest$Person]...)]
```

## How to extract the values
```kotlin
val team: Team = objectMapper.readValue(teamJson)
println(team.persons)
val validPersons = team.persons.mapNotNull { errorBox ->
    errorBox.getOrHandle { error ->
        // Log error or report metric here log.error(error.message)
        null
    }
}
validPersons shouldHaveSize 1
```

For more examples check [ErrorBoxTest](https://github.com/nicolaslattuada/error-box/blob/master/src/test/kotlin/com/nicolattu/ErrorBoxTest.kt) 

Note that the ErrorBox is a type alias from https://arrow-kt.io Either<Exception, T>, 
and therefore has all the super powers of it [see the doc](https://arrow-kt.io/docs/apidocs/arrow-core-data/arrow.core/-either/)  
