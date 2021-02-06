import SwiftUI
import serif_shared

func version() -> String {
    return MatrixClient().version()
}

struct ContentView: View {
    var body: some View {
        Text(version())
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
