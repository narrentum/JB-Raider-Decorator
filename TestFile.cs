// Test file for Code Decorator plugin
using _this;

public class TestClass 
{
    private string 				_string = "test variable";

	private delegate void 		TestDelegate();

	public static void Main()
	{

	}
    
    public void TestMethod() 
	{
        // TODO: [Fixed] This task is completed
        // TODO: [QA] This needs review
        // TODO: [InProgress] Currently working on this
        
        console.log("Testing console output");
        console.error("Testing error output");
        
        // [CRITICAL] This is a critical comment
        // [URGENT] This needs immediate attention
        
        var someVar = _this + " additional text";

		TestClass.Main();
		_this.Main();
    }
}