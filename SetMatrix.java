import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class SetMatrix{
	
	static FileChannel file;
	static int Values[][] = new int[2048][2048];

	public static void main(String [] args){
		/* Populate Array */
		for (int i = 0; i < 2048; i++) {
			for (int j = 0; j < 2048; j++) {
				Values[i][j] = 5;
				System.out.println(Values[i][j]);
			}
		}
	
		
		try{
			/* Create file */
			file = new FileOutputStream("fc.txt").getChannel();
		}
		catch (IOException e){
			e.printStackTrace();
		}
		
		/* Save Array to file */
		storeFC(file, Values);
		
		
		
		
		
	}
	
	

	private static void storeFC(FileChannel file, int[][] QValue) {
		try {
			ByteBuffer buf = ByteBuffer.allocateDirect(4 * 2048 * 2048);
			for (int i = 0; i < 2048; i++) {
				for (int j = 0; j < 2048; j++){
					buf.put(String.valueOf(QValue[i][j]).getBytes());
					buf.put("\n".getBytes());
				}
			}
			buf.flip();
			file.write(buf);
			buf.clear();
			// file.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}