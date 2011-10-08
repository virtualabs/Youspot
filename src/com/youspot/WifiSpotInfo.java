package com.youspot;

import java.util.Comparator;

/**
 * This class stores a given wifi spot info (known spot, last seen, signal strength)
 * 
 * @author virtualabs
 *
 */

public class WifiSpotInfo {
		
	private WifiSpot m_spot;
	private boolean m_known;
	private int	 	m_signal;
	private int[] 	m_measures;
	private int		m_seen;
	private boolean	m_just_created;
	private final static int NB_MEASURES = 10;
	
	/**
	 * Constructor
	 * 
	 * @param spot	Reference to a particular wifi spot
	 * 
	 */
	
	public WifiSpotInfo(WifiSpot spot) {
		this.m_spot = spot;
		this.m_seen = 20;
		this.m_measures = new int[NB_MEASURES];
		this.m_signal = 0;
		this.m_just_created = true;
		this.m_known = false;
	}
	
	
	/**
	 * Get the average signal strength of a given spot 
	 * 
	 * @return int Spot's signal strength
	 */
	
	public int getSignal() {
		return m_signal;
	}
	
	
	/**
	 * Update signal strength measures in order to compute signal level 
	 * 
	 * @param signal signal measure
	 */
	
	public void updateSignal(int signal) {
		int i,s;
		
		/* add signal to measures */
		if (m_just_created)
		{
			for (i=1;i<NB_MEASURES;i++)
				m_measures[i] = signal;
			m_just_created = false;
		}
		else
		{
			/* shift */
			for (i=1;i<NB_MEASURES;i++)
				m_measures[i-1]=m_measures[i];
			/* add */
			m_measures[NB_MEASURES-1] = signal;
		}
		
		/* compute signal average */
		s=0;
		for (i=0;i<NB_MEASURES;i++)
			s += m_measures[i];
		m_signal = s/NB_MEASURES;
	}
	
	
	/**
	 * Get the underlying WifiSpot referenced by the current instance [GETTER]
	 * 
	 * @return WifiSpot Referenced spot
	 */
	
	public WifiSpot getSpot() {
		return m_spot;
	}
	
	
	/**
	 * Check if the underlying spot was recently seen.
	 * 
	 * @return boolean True if the spot was recently seen, False otherwise
	 */
	
	public boolean wasSeen() {
		m_seen = m_seen - 1;
		return (m_seen==0);
	}
	
	
	/**
	 * Declares the underlying spot as seen 
	 */
	
	public void see() {
		m_seen = 20;
	}

	
	/**
	 * Mark the underlying spot as already known [SETTER] 
	 */
	
	public void markAsKnown() {
		m_known = true;
	}
	
	
	/**
	 * Check if the underlying spot is already known [GETTER]
	 *
	 * @return boolean
	 */
	
	public boolean isKnown() {
		return m_known;
	}
	
	
	/**
	 * 
	 * @author virtualabs
	 *
	 * Dedicated comparator for WifiSpotInfo, used to sort a list of WifiSpotInfo by
	 * signal strength
	 *
	 */
	
	public static class WsiComparator implements Comparator<WifiSpotInfo> {
		
		/**
		 * Constructor
		 */
		
		public WsiComparator() {
			super();
		}
		
		
		/**
		 * Compares two WifiSpotInfo instances. If a spot is known, then it comes first.
		 * Otherwise the spot having the best signal strength comes first.
		 * 
		 * @param WifiSpotInfo wsi1 First spot to compare to
		 * @param WifiSpotInfo wsi2 Second spot to compare to
		 * 
		 */
		
		public int compare(WifiSpotInfo wsi1, WifiSpotInfo wsi2)
		{
			
			if (wsi1.isKnown() && !wsi2.isKnown())
				return -1;
			else if (!wsi1.isKnown() && wsi2.isKnown())
				return 1;
			else
			{
				if (wsi1.getSignal() < wsi2.getSignal())
					return 1;
				else if (wsi1.getSignal() > wsi2.getSignal())
					return -1;
				else
					return 0;				
			}
		}
	}
	
}
