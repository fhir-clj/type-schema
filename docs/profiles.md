# Profiles

**Status**: In progress

What profile can do for data entities:

1. Change cordinality (require, exclude)
2. Restrict number of the choice types.
3. Restrict binding, including setting field to constant value.
4. FHIR Path constraint
5. Slice

```python

class A:
    a: string

class ABC(A):
    b: string
    c: string

class BC(ABC):
    a: None
```

## Why we need inheritance?

```java
class A implements GetIdI {
   String id;
}

class B implements GetIdI {
   String id;
}

public void listId(List<GetIdI> objects) {
    for (GetIdI o : objects) {
        System.out.println(getId(o));
    }
}
```

```java
interface GetA {    String getA();}
interface GetB {    String getB();}
interface GetC { String getC();}

class A implements GetA {
    String a;
}

class ABC extends A implements GetA, GetB, GetC {
    String b;
    String c;
}
// NOTE: don't work because GetA is already implemented by ABC
class BC extends ABC implements GetB, GetC {
}
```

```java
interface GetA {    String getA();}
interface GetB {    String getB();}
interface GetC { String getC();}

class A implements GetA {
    String a;
}

class ABC  implements GetA, GetB, GetC {
    String a;
    String b;
    String c;
}
// NOTE: don't work because GetA is already implemented by ABC
class BC extends ABC implements GetB, GetC {
    String b;
    String c;
}

public void listId(List<GetIdI> objects) {
    for (GetIdI o : objects) {
        System.out.println(getId(o));
    }
}

```

## Put it in a run-time
