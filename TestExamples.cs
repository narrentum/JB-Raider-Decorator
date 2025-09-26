using System;
using _this; // Условие для подсветки _this

namespace TestHighlighting 
{
    public class ExampleClass 
    {
        public void TestMethod() 
        {
            // Примеры для тестирования подсветки:
            
            // 1. _this highlighting (синий фон)
            var result = _this.SomeMethod();
            _this.AnotherMethod();
            
            // 2. TODO [Fixed] - должен быть зачеркнутый серый
            // TODO: Fix this bug [Fixed]
            
            // 3. TODO [QA] - оранжевый фон
            // TODO: Check this with QA [QA]
            
            // 4. TODO [InProgress] - синий фон, жирный
            // TODO: Working on this [InProgress]
            
            // 5. FIXME [Fixed] - зачеркнутый серый
            // FIXME: Memory leak here [Fixed]
            
            // 6. React components (при наличии import React)
            // React component test
            
            // 7. console.log - желтый фон
            console.log("Debug message");
            
            // 8. [CRITICAL] - красный фон, жирный, подчеркнутый
            // [CRITICAL] This needs immediate attention!
        }
    }
}