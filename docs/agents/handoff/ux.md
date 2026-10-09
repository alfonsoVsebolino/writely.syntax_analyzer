help me shape up a spec/ticket addressing some user experience issues I encountered during manual testing.
  
1. In opened file code line/snippets that can be ingested for analysis, clicking on the 'x' button beside the tab
  indicator for the opened file transitions to 'untitled.py' from 'filename.py'. We need to omit this behavior and
  simply close the open file/code block for inspection and display an 'empty state' message on the main column/content
  pane.
  
2. When clicking a new file on the explorer column (left-hand side), it does not display the code in the middle (main
  content pane) after closing the main code editor middle column. It does say 'Loaded Main.java' or the corresponding
  file name but no code lines are displayed.
 
 3. The diagnostics tab with the semantically colored pills (when an error is detected) wraps into a string of three
  dots '...' due to insufficient width of the frontend element. We need to patch up the rendering of this pill so that
  the entire string can be displayed, suggest ways on how we can handle this if the screen size is insufficient
  (currently, on my laptop screen there's no option for a scroll bar for the main stage and the diagnostics tab which
  contains a lot of textual data to be rendered
