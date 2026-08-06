for filename in ["app/src/test/java/com/example/data/repository/DocxParserTest.kt"]:
    with open(filename, "r") as f:
        content = f.read()
    
    if "import org.robolectric.RobolectricTestRunner" not in content:
        content = content.replace("import org.junit.Test", "import org.junit.Test\nimport org.junit.runner.RunWith\nimport org.robolectric.RobolectricTestRunner\nimport org.robolectric.annotation.Config")
        content = content.replace("class DocxParserTest", "@RunWith(RobolectricTestRunner::class)\n@Config(sdk = [36])\nclass DocxParserTest")
        
        with open(filename, "w") as f:
            f.write(content)
