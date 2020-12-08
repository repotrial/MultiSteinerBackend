import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.Objects;


public class Link extends DefaultWeightedEdge implements Comparable<Link>{

    private static final long serialVersionUID = 7624431279214855512L;

    private final Vertex source, target;
    private String uniprotID_Concatinated;
    private Double weight;

    public Link (Vertex source, Vertex target, Double weight) {
        super();

        this.source = source;
        this.target = target;
        this.uniprotID_Concatinated = source.getUniprotID() + " (-) " + target.getUniprotID();
        this.weight = weight;
    }


    public String getUniprotID_Concatinated() {
        return uniprotID_Concatinated;
    }

    public Vertex getSource() {

        return source;
    }

    public Vertex getTarget() {

        return target;
    }

    public double getWeight() {

        return this.weight;
    }

    @Override
    public String toString()
    {
        return "(" + getSource().toString() + ":" + getTarget().toString() + "_weight=" + weight+")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Link link = (Link) o;
        return Objects.equals(uniprotID_Concatinated, link.uniprotID_Concatinated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uniprotID_Concatinated);
    }

    @Override
    public int compareTo(Link link) {
        return this.uniprotID_Concatinated.compareTo(link.getUniprotID_Concatinated());
    }


}
