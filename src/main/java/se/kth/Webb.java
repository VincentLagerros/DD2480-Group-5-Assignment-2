package se.kth;

/**
 * Formating strings for the web
 */
public class Webb {
    static String nothingFound = """
            <a>Nothing found</a>""";
    static String buildHeader = """
            <tr id="headerDesign">
                        <th>Commit Identifier</th>
                        <th>Status</th>
                        <th>Build Date</th>
                        <th>Build Log</th>
                    </tr>""";
    static String dirHeader = """
            <tr id="headerDesign">
                        <th>Folder</th>
                    </tr>""";
    static String buildRow = """
            <tr>
                <td>%s</td>
                <td>%s</td>
                <td>%s</td>
                <td><a href="/%s">Log</a></td>
            </tr>""";
    static String dirRow = """
            <tr>
                <td><a href="/%s">%s</a></td>
            </tr>""";


    static String template = """
            <!DOCTYPE html>
            <html lang="en">
            <meta charset="UTF-8">
            
            <meta name="viewport" content="width=device-width,initial-scale=1">
            
            <style>
                table {
                    border:1px solid black;
                    margin: 0 auto;\s
                }
            
                th, td {
                    border:1px solid black;
                    width: 500px;
                    height: 60px;
                }
            
                #headerDesign {
                    background-color:  gainsboro;
                    font-size: 25px;\s
                }
            
                #statusPass{
                    background-color: green;
                    text-align: center;
                }
            
                #statusFail{
                    background-color: red;
                    text-align: center;
                }
            
            
            </style>
            
            <body>
            
            <div>
                <table>
                    %s
                </table>
                <table>
                    %s
                </table>
            </div>
            
            </body>
            </html>""";
}
