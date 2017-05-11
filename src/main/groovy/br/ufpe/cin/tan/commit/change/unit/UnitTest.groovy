package br.ufpe.cin.tan.commit.change.unit

/***
 * Represents a generic unit test. Different unit test frameworks model such a test in different ways.
 */
class UnitTest {

    String name
    String path
    List lines

    @Override
    String toString() {
        return "Unit test: $name ($path: ${lines.first()}-${lines.last()})"
    }

}
