using System;

public class TestClass 
{
    private string _this = "test variable";  // Should be highlighted in blue
    
    public void TestMethod()
    {
        // TODO: implement this method         // Should be highlighted in yellow
        var result = _this + " processed";    // _this should be highlighted
        Console.WriteLine(result);
    }
    
    // TODO: add more tests                   // Another TODO comment
}
