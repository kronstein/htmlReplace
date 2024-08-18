# htmlReplace
 
 [![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

HTML tag replacement tool for Kotlin (Java).

Required dependencies:
- [JSoup](https://jsoup.org/)
- [kotlinx.html](https://github.com/Kotlin/kotlinx.html)

Example usage:

```kotlin
import kotlinx.html.*

htmlReplace(
    html = """
        <p>Allo, <q>Mars</q></p>
    """.trimIndent(),

    cssQuery = ":containsOwn(Mars)" to {
        a {
            href = "https://www.mars.com"
            unsafe {
                raw(it.outerHtml())
            }
        }
    }
)
```

Output:
```html
<p>Allo, <a href="https://mars.com"><q>Mars</q></a></p>
```

How to use:

- define `cssQuery` using [JSoup syntax](https://jsoup.org/cookbook/extracting-data/selector-syntax);
- (optional) access original element properties: `it.attributes()`, `it.html()`.
