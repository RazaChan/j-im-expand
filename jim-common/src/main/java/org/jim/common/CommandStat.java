package org.jim.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jim.common.packets.Command;

/**
 * 
 * @author wchao 
 *
 */
public class CommandStat
{

	public final static Map<Command, CommandStat> commandAndCount = new ConcurrentHashMap<>();
	
	public final AtomicLong received = new AtomicLong();
	public final AtomicLong handled = new AtomicLong();
	public final AtomicLong sent = new AtomicLong();

	public static CommandStat getCount(Command command)
	{
		if (command == null) {
			return null;
		}
		CommandStat ret = commandAndCount.get(command);
		if (ret != null)
		{
			return ret;
		}

		synchronized (commandAndCount)
		{
			ret = commandAndCount.get(command);
			if (ret != null)
			{
				return ret;
			}
			ret = new CommandStat();
			commandAndCount.put(command, ret);
		}
		return ret;
	}
	

	/**
	 * 
	 *
	 * @author: wchao
	 * 2016年12月6日 下午5:32:31
	 * 
	 */
	public CommandStat()
	{
	}

	/**
	 * @param args
	 *
	 * @author: wchao
	 * 2016年12月6日 下午5:32:31
	 * 
	 */
	public static void main(String[] args)
	{
	}


	/**
	 * @return the receivedCount
	 */
	public AtomicLong getReceived()
	{
		return received;
	}


	/**
	 * @return the handledCount
	 */
	public AtomicLong getHandled()
	{
		return handled;
	}


	/**
	 * @return the sentCount
	 */
	public AtomicLong getSent()
	{
		return sent;
	}

}
